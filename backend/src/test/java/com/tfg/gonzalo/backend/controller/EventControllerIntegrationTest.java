package com.tfg.gonzalo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

  private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEventInitializesOrganizerAndGameState() throws Exception {
      String organizerToken = registerOrganizer("organizer1", "orga@example.com", "Password123");

        String requestBody = """
                {
                  "name": "Evento Prueba",
                  "configuredPlayerCount": 8,
                  "timeLimitMinutes": 180
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Evento Prueba"))
          .andExpect(jsonPath("$.configuredPlayerCount").value(0))
          .andExpect(jsonPath("$.villainHpMax").value(0))
          .andExpect(jsonPath("$.villainHpCurrent").value(0))
                .andExpect(jsonPath("$.schemeThreatTarget").value(0))
                .andExpect(jsonPath("$.timerRemainingSeconds").value(10800))
                .andExpect(jsonPath("$.organizerUsername").value("organizer1"));
    }

    @Test
    void createEventReturnsSelectedInterPodDefinitionImageUrlWithHyphenatedRoute() throws Exception {
      String organizerToken = registerOrganizer("organizer2", "orga2@example.com", "Password123");

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "card.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        MvcResult createDefinitionResult = mockMvc.perform(multipart("/api/inter-pod-card-definitions")
                        .file(image)
                        .param("name", "CUSTOM_IMAGE_TEST_CARD")
                        .param("cardCategory", "ESBIRRO")
                        .param("defaultHpMax", "8")
                        .param("defaultStatus", "REVELADA")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isCreated())
                .andReturn();

        long definitionId = extractId(createDefinitionResult.getResponse().getContentAsString());

        String requestBody = """
                {
                  "name": "Evento con carta inter-pod",
                  "configuredPlayerCount": 8,
                  "timeLimitMinutes": 180,
                  "interPodCardDefinitionIds": [%d]
                }
                """.formatted(definitionId);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions[0].id").value((int) definitionId))
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions[0].imageUrl")
                        .value("/api/inter-pod-card-definitions/" + definitionId + "/image"));
    }

                @Test
                void createEventReturnsSelectedInterPodDefinitionExternalImageUrl() throws Exception {
                  String organizerToken = registerOrganizer("organizer2b", "orga2b@example.com", "Password123");
                  String externalImageUrl = "https://marvelcdb.com/bundles/cards/55041.jpg";

              MvcResult createDefinitionResult = mockMvc.perform(multipart("/api/inter-pod-card-definitions")
                  .param("name", "CUSTOM_REMOTE_IMAGE_TEST_CARD")
                  .param("cardCategory", "ESBIRRO")
                  .param("defaultHpMax", "8")
                  .param("defaultStatus", "REVELADA")
                  .param("imageUrl", externalImageUrl)
                  .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(externalImageUrl))
                .andReturn();

              long definitionId = extractId(createDefinitionResult.getResponse().getContentAsString());

              String requestBody = """
                {
                  "name": "Evento con carta inter-pod remota",
                  "configuredPlayerCount": 8,
                  "timeLimitMinutes": 180,
                  "interPodCardDefinitionIds": [%d]
                }
                """.formatted(definitionId);

              mockMvc.perform(post("/api/events")
                  .header("Authorization", "Bearer " + organizerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions[0].id").value((int) definitionId))
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions[0].imageUrl").value(externalImageUrl));
                }

                @Test
                void updateEventUpdatesAndClearsSelectedInterPodDefinitions() throws Exception {
              String organizerToken = registerOrganizer("organizer3", "orga3@example.com", "Password123");

              long firstDefinitionId = createInterPodCardDefinition(organizerToken, "FIRST_CUSTOM_CARD");
              long secondDefinitionId = createInterPodCardDefinition(organizerToken, "SECOND_CUSTOM_CARD");
              long eventId = createEvent(
                organizerToken,
                """
                  {
                    "name": "Evento editable",
                    "timeLimitMinutes": 180,
                    "interPodCardDefinitionIds": [%d]
                  }
                  """.formatted(firstDefinitionId)
              );

              mockMvc.perform(put("/api/events/{eventId}", eventId)
                  .header("Authorization", "Bearer " + organizerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                    """
                      {
                        "name": "Evento editable",
                        "timeLimitMinutes": 180,
                        "interPodCardDefinitionIds": [%d]
                      }
                      """.formatted(secondDefinitionId)
                  ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions.length()").value(1))
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions[0].id").value((int) secondDefinitionId));

              mockMvc.perform(put("/api/events/{eventId}", eventId)
                  .header("Authorization", "Bearer " + organizerToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                    """
                      {
                        "name": "Evento editable",
                        "timeLimitMinutes": 180,
                        "interPodCardDefinitionIds": []
                      }
                      """
                  ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions").isArray())
                .andExpect(jsonPath("$.selectedInterPodCardDefinitions").isEmpty());
                }

    private String registerOrganizer(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new IllegalStateException("No se ha encontrado accessToken en la respuesta JSON.");
        }
        return matcher.group(1);
    }

      private long createInterPodCardDefinition(String organizerToken, String name) throws Exception {
        MvcResult createDefinitionResult = mockMvc.perform(multipart("/api/inter-pod-card-definitions")
                .param("name", name)
                .param("cardCategory", "ESBIRRO")
                .param("defaultHpMax", "8")
                .param("defaultStatus", "REVELADA")
                .header("Authorization", "Bearer " + organizerToken))
            .andExpect(status().isCreated())
            .andReturn();

        return extractId(createDefinitionResult.getResponse().getContentAsString());
      }

      private long createEvent(String organizerToken, String requestBody) throws Exception {
        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                .header("Authorization", "Bearer " + organizerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn();

        return extractId(createEventResult.getResponse().getContentAsString());
      }

      private long extractId(String responseBody) {
        Matcher matcher = ID_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
          throw new IllegalStateException("No se ha encontrado un campo id en la respuesta JSON.");
        }
        return Long.parseLong(matcher.group(1));
      }
}