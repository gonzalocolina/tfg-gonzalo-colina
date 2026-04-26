package com.tfg.gonzalo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

      private long extractId(String responseBody) {
        Matcher matcher = ID_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
          throw new IllegalStateException("No se ha encontrado un campo id en la respuesta JSON.");
        }
        return Long.parseLong(matcher.group(1));
      }
}