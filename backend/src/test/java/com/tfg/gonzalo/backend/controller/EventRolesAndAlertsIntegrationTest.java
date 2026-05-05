package com.tfg.gonzalo.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
class EventRolesAndAlertsIntegrationTest {

    private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"accessToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern REPRESENTATIVE_CODE_PATTERN = Pattern.compile("\\\"representativeCode\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    private record GroupCreationResult(Long groupId, String representativeCode) {
    }

    @Test
    void coOrganizerCanOperateOnlyAssignedResponsibilities() throws Exception {
        long eventId = createEvent("evento-coorg", 4, 180);
        String organizerToken = loginOrganizer("evento-coorg", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);

        mockMvc.perform(post("/api/events/{eventId}/roles/co-organizers", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "coorg1",
                                  "email": "coorg1@example.com",
                                  "password": "Password123",
                                  "responsibilities": ["VILLAIN_HP", "THREAT"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("coorg1"));

        String coOrganizerToken = loginCoOrganizer(eventId, "coorg1", "Password123");

        mockMvc.perform(patch("/api/events/{eventId}/game-state/villain-hp", eventId)
                        .header("Authorization", "Bearer " + coOrganizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": 50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(50));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + coOrganizerToken))
                .andExpect(status().isForbidden());
    }

    @Test
        void interPodCardsEnforceSamePodAndAreQueryableInLogs() throws Exception {
      long eventId = createEvent("evento-cartas", 8, 180);
      String organizerToken = loginOrganizer("evento-cartas", "Password123");

      long podAId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult groupA = createGroupAndGetResult(eventId, podAId, organizerToken, "A1", 4);

      long podBId = createPod(eventId, organizerToken, "Pod B");
      GroupCreationResult groupB = createGroupAndGetResult(eventId, podBId, organizerToken, "B1", 4);

      String representativeAToken = loginGroup(eventId, groupA.representativeCode());
      String representativeBToken = loginGroup(eventId, groupB.representativeCode());

      mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

      MockMultipartFile createPayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "representativeCode": "%s",
            "cardName": "THE_MANGOG"
          }
          """.formatted(groupA.representativeCode()).getBytes(StandardCharsets.UTF_8)
      );

      MvcResult cardResult = mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(createPayload)
          .header("Authorization", "Bearer " + representativeAToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardName").value("THE_MANGOG"))
        .andExpect(jsonPath("$.cardCategory").value("ESBIRRO"))
        .andExpect(jsonPath("$.hpCurrent").value(8))
        .andReturn();

      long cardId = extractId(cardResult.getResponse().getContentAsString());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/damage", eventId, cardId)
          .header("Authorization", "Bearer " + representativeBToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "damage": 2
            }
            """.formatted(groupB.representativeCode())))
        .andExpect(status().isForbidden());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/damage", eventId, cardId)
          .header("Authorization", "Bearer " + representativeAToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "damage": 3
            }
            """.formatted(groupA.representativeCode())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hpCurrent").value(5));

      mockMvc.perform(patch("/api/events/{eventId}/inter-pod-cards/{cardId}/status", eventId, cardId)
          .header("Authorization", "Bearer " + organizerToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "status": "DERROTADA"
            }
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DERROTADA"))
        .andExpect(jsonPath("$.hpCurrent").value(0));

      mockMvc.perform(get("/api/events/{eventId}/inter-pod-cards", eventId)
          .header("Authorization", "Bearer " + organizerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cardName").value("THE_MANGOG"));

      mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
          .header("Authorization", "Bearer " + organizerToken)
          .param("groupId", String.valueOf(groupA.groupId()))
          .param("actionType", "INTER_POD_CARD_CREATED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actionType").value("INTER_POD_CARD_CREATED"));
    }

    @Test
    void representativesCannotOperateInterPodCardsWhileGameIsPaused() throws Exception {
      long eventId = createEvent("evento-cartas-pausa", 4, 180);
      String organizerToken = loginOrganizer("evento-cartas-pausa", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      String representativeToken = loginGroup(eventId, group.representativeCode());

      mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

      MockMultipartFile createPayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "representativeCode": "%s",
            "cardName": "THE_MANGOG"
          }
          """.formatted(group.representativeCode()).getBytes(StandardCharsets.UTF_8)
      );

      long cardId = extractId(mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(createPayload)
          .header("Authorization", "Bearer " + representativeToken))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString());

      mockMvc.perform(post("/api/events/{eventId}/game-state/timer/pause", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/damage", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "damage": 2
            }
            """.formatted(group.representativeCode())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("está pausada")));

      mockMvc.perform(patch("/api/events/{eventId}/inter-pod-cards/{cardId}/status", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "status": "DERROTADA"
            }
            """.formatted(group.representativeCode())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("está pausada")));

      MockMultipartFile pausedCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "representativeCode": "%s",
            "cardName": "THE_MANGOG"
          }
          """.formatted(group.representativeCode()).getBytes(StandardCharsets.UTF_8)
      );

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(pausedCreatePayload)
          .header("Authorization", "Bearer " + representativeToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("está pausada")));
    }

    @Test
    void representativesCannotOperateInterPodCardsWhileTimerIsReady() throws Exception {
      long eventId = createEvent("evento-cartas-ready", 4, 180);
      String organizerToken = loginOrganizer("evento-cartas-ready", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      String representativeToken = loginGroup(eventId, group.representativeCode());

      MockMultipartFile organizerCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "representativeCode": "%s",
            "cardName": "THE_MANGOG"
          }
          """.formatted(group.representativeCode()).getBytes(StandardCharsets.UTF_8)
      );

      long cardId = extractId(mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(organizerCreatePayload)
          .header("Authorization", "Bearer " + organizerToken))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString());

      mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("READY"));

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/damage", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "damage": 2
            }
            """.formatted(group.representativeCode())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));

      mockMvc.perform(patch("/api/events/{eventId}/inter-pod-cards/{cardId}/status", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "representativeCode": "%s",
              "status": "DERROTADA"
            }
            """.formatted(group.representativeCode())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));

      MockMultipartFile readyCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "representativeCode": "%s",
            "cardName": "THE_MANGOG"
          }
          """.formatted(group.representativeCode()).getBytes(StandardCharsets.UTF_8)
      );

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(readyCreatePayload)
          .header("Authorization", "Bearer " + representativeToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));
    }

    @Test
    void optionalTimerCanBeDisabledAndGameCanStillStart() throws Exception {
        long eventId = createEvent("evento-sin-timer", 2, 0);
        String organizerToken = loginOrganizer("evento-sin-timer", "Password123");

        mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.snapshot.timerStatus").value("NOT_CONFIGURED"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest());
    }

    private long createEvent(String username, int players, int timeLimitMinutes) throws Exception {
        String organizerToken = registerOrganizer(username);

        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evento %s",
                                  "configuredPlayerCount": %d,
                                  "timeLimitMinutes": %d
                                }
                                """.formatted(username, players, timeLimitMinutes)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result.getResponse().getContentAsString());
    }

    private String registerOrganizer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "Password123"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();

        Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new IllegalStateException("No se ha encontrado accessToken en la respuesta JSON.");
        }
        return matcher.group(1);
    }

    private long createPod(long eventId, String organizerToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/pods", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "sortOrder": 0
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

          private GroupCreationResult createGroupAndGetResult(long eventId, long podId, String organizerToken,
                                    String identifier, int playerCount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/pods/{podId}/groups", eventId, podId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "%s",
                                  "playerCount": %d
                                }
                                """.formatted(identifier, playerCount)))
                .andExpect(status().isCreated())
                .andReturn();

            String body = result.getResponse().getContentAsString();
            Matcher matcher = REPRESENTATIVE_CODE_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("No se ha encontrado representativeCode en la respuesta JSON.");
        }
            return new GroupCreationResult(extractId(body), matcher.group(1));
    }

    private String loginOrganizer(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/organizer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return extractToken(result.getResponse().getContentAsString());
    }

    private String loginCoOrganizer(long eventId, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/co-organizer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": %d,
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(eventId, username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return extractToken(result.getResponse().getContentAsString());
    }

    private String loginGroup(long eventId, String representativeCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/group/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": %d,
                                  "code": "%s"
                                }
                                """.formatted(eventId, representativeCode)))
                .andExpect(status().isOk())
                .andReturn();

        return extractToken(result.getResponse().getContentAsString());
    }

    private String extractToken(String responseBody) {
        Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
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
