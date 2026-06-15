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
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import com.tfg.gonzalo.backend.repository.InterPodCardDefinitionRepository;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterPodCardDefinitionRepository interPodCardDefinitionRepository;

    private record GroupCreationResult(Long groupId, String identifier) {
    }

    @Test
    void organizerCanListAssignedCoOrganizers() throws Exception {
        long eventId = createEvent("evento-lista-coorg", 4, 180);
        String organizerToken = loginOrganizer("evento-lista-coorg", "Password123");
        String coOrganizerToken = registerOrganizer("coorg-lista");

        long invitationId = inviteCoOrganizer(eventId, organizerToken, "coorg-lista", "VILLAIN_HP", "TIMER");
        acceptCoOrganizerInvitation(invitationId, coOrganizerToken);

        mockMvc.perform(get("/api/events/{eventId}/roles/co-organizers", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("coorg-lista"))
                .andExpect(jsonPath("$[0].email").value("coorg-lista@example.com"))
                .andExpect(jsonPath("$[0].responsibilities[0]").value("VILLAIN_HP"))
                .andExpect(jsonPath("$[0].responsibilities[1]").value("TIMER"))
                .andExpect(jsonPath("$[0].invitationId").value((int) invitationId));
    }

    @Test
    void coOrganizerCanOperateOnlyAssignedResponsibilities() throws Exception {
        long eventId = createEvent("evento-coorg", 4, 180);
        String organizerToken = loginOrganizer("evento-coorg", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);

        String coOrganizerToken = registerOrganizer("coorg1");
        long invitationId = inviteCoOrganizer(eventId, organizerToken, "coorg1", "VILLAIN_HP", "THREAT");
        acceptCoOrganizerInvitation(invitationId, coOrganizerToken);

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

      String representativeAToken = loginGroup(eventId, groupA.identifier());
      String representativeBToken = loginGroup(eventId, groupB.identifier());
      long mangogDefinitionId = predefinedInterPodCardDefinitionId("THE_MANGOG");

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
            "interPodCardDefinitionId": %d
          }
          """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)
      );

      MvcResult cardResult = mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
          .file(createPayload)
          .header("Authorization", "Bearer " + representativeAToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardName").value("THE_MANGOG"))
        .andExpect(jsonPath("$.cardCategory").value("ESBIRRO"))
        .andExpect(jsonPath("$.metricCurrent").value(10))
        .andReturn();

      long cardId = extractId(cardResult.getResponse().getContentAsString());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
          .header("Authorization", "Bearer " + representativeBToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "amount": 2
            }
            """))
        .andExpect(status().isForbidden());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
          .header("Authorization", "Bearer " + representativeAToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "amount": 3
            }
            """))
        .andExpect(status().isOk())
          .andExpect(jsonPath("$.metricCurrent").value(7));

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
        .andExpect(jsonPath("$.metricCurrent").value(0));

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
          .header("Authorization", "Bearer " + representativeAToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "amount": 1
            }
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("ya está derrotada")));

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
    void interPodPlansTrackThreatProgress() throws Exception {
      long eventId = createEvent("evento-plan-threat", 4, 180);
      String organizerToken = loginOrganizer("evento-plan-threat", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      String representativeToken = loginGroup(eventId, group.identifier());
      long doorDefinitionId = predefinedInterPodCardDefinitionId("DOOR_BETWEEN_WORLDS");

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
                  "interPodCardDefinitionId": %d
                }
                """.formatted(doorDefinitionId).getBytes(StandardCharsets.UTF_8)
      );

      long cardId = extractId(mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(createPayload)
                      .header("Authorization", "Bearer " + representativeToken))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.cardCategory").value("PLAN"))
              .andExpect(jsonPath("$.metricCurrent").value(7))
              .andReturn()
              .getResponse()
              .getContentAsString());

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
                      .header("Authorization", "Bearer " + representativeToken)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                        {
                          "amount": 3
                        }
                        """))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.metricCurrent").value(4))
              .andExpect(jsonPath("$.status").value("REVELADA"));

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
                      .header("Authorization", "Bearer " + representativeToken)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                        {
                          "amount": 5
                        }
                        """))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.metricCurrent").value(0))
              .andExpect(jsonPath("$.status").value("DERROTADA"));

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
                      .header("Authorization", "Bearer " + representativeToken)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                        {
                          "amount": 1
                        }
                        """))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.message").value(containsString("ya está derrotada")));
    }

    @Test
    void interPodCardsReportableDefinitionsExcludeAlreadyReportedByCurrentTable() throws Exception {
      long eventId = createEvent("evento-cartas-reportables", 4, 180);
      String organizerToken = loginOrganizer("evento-cartas-reportables", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      String representativeToken = loginGroup(eventId, group.identifier());
      long mangogDefinitionId = predefinedInterPodCardDefinitionId("THE_MANGOG");

      mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                      .header("Authorization", "Bearer " + organizerToken))
              .andExpect(status().isOk());

      mockMvc.perform(get("/api/events/{eventId}/inter-pod-card-definitions/reportable", eventId)
                      .header("Authorization", "Bearer " + representativeToken))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$[?(@.name == 'THE_MANGOG')]").exists());

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeToken))
              .andExpect(status().isCreated());

      mockMvc.perform(get("/api/events/{eventId}/inter-pod-card-definitions/reportable", eventId)
                      .header("Authorization", "Bearer " + representativeToken))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$[?(@.name == 'THE_MANGOG')]").doesNotExist());
    }

    @Test
    void interPodCardsScaleStatsByNumberOfTablesInEachPod() throws Exception {
      long eventId = createEvent("evento-cartas-escaladas", 16, 180);
      String organizerToken = loginOrganizer("evento-cartas-escaladas", "Password123");

      long podAId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult groupA1 = createGroupAndGetResult(eventId, podAId, organizerToken, "A1", 4);
      GroupCreationResult groupA2 = createGroupAndGetResult(eventId, podAId, organizerToken, "A2", 4);
      createGroupAndGetResult(eventId, podAId, organizerToken, "A3", 4);

      long podBId = createPod(eventId, organizerToken, "Pod B");
      GroupCreationResult groupB1 = createGroupAndGetResult(eventId, podBId, organizerToken, "B1", 4);

      String representativeAToken = loginGroup(eventId, groupA1.identifier());
      String representativeA2Token = loginGroup(eventId, groupA2.identifier());
      String representativeBToken = loginGroup(eventId, groupB1.identifier());
      long mangogDefinitionId = predefinedInterPodCardDefinitionId("THE_MANGOG");
      long doorDefinitionId = predefinedInterPodCardDefinitionId("DOOR_BETWEEN_WORLDS");

      mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                      .header("Authorization", "Bearer " + organizerToken))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeAToken))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.metricMax").value(30))
              .andExpect(jsonPath("$.metricCurrent").value(30));

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeAToken))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.message").value(containsString("ya ha indicado")));

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeA2Token))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.metricMax").value(30))
              .andExpect(jsonPath("$.metricCurrent").value(30));

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeBToken))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.metricMax").value(10))
              .andExpect(jsonPath("$.metricCurrent").value(10));

      mockMvc.perform(multipart("/api/events/{eventId}/inter-pod-cards", eventId)
                      .file(new MockMultipartFile(
                              "payload",
                              "payload.json",
                              MediaType.APPLICATION_JSON_VALUE,
                              """
                                {
                                  "interPodCardDefinitionId": %d
                                }
                                """.formatted(doorDefinitionId).getBytes(StandardCharsets.UTF_8)))
                      .header("Authorization", "Bearer " + representativeAToken))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.metricMax").value(21))
              .andExpect(jsonPath("$.metricCurrent").value(21));
    }

    @Test
    void representativesCannotOperateInterPodCardsWhileGameIsPaused() throws Exception {
      long eventId = createEvent("evento-cartas-pausa", 4, 180);
      String organizerToken = loginOrganizer("evento-cartas-pausa", "Password123");

      long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      String representativeToken = loginGroup(eventId, group.identifier());
      long mangogDefinitionId = predefinedInterPodCardDefinitionId("THE_MANGOG");

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
            "interPodCardDefinitionId": %d
          }
          """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)
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

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "amount": 2
            }
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("está pausada")));

      mockMvc.perform(patch("/api/events/{eventId}/inter-pod-cards/{cardId}/status", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
                          "status": "DERROTADA"
            }
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("está pausada")));

      MockMultipartFile pausedCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "interPodCardDefinitionId": %d
          }
          """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)
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
      String representativeToken = loginGroup(eventId, group.identifier());
      long mangogDefinitionId = predefinedInterPodCardDefinitionId("THE_MANGOG");

      MockMultipartFile organizerCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "sourceGroupId": %d,
            "interPodCardDefinitionId": %d
          }
          """.formatted(group.groupId(), mangogDefinitionId).getBytes(StandardCharsets.UTF_8)
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

      mockMvc.perform(post("/api/events/{eventId}/inter-pod-cards/{cardId}/progress", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "amount": 2
            }
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));

      mockMvc.perform(patch("/api/events/{eventId}/inter-pod-cards/{cardId}/status", eventId, cardId)
          .header("Authorization", "Bearer " + representativeToken)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
                          "status": "DERROTADA"
            }
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));

      MockMultipartFile readyCreatePayload = new MockMultipartFile(
        "payload",
        "payload.json",
        MediaType.APPLICATION_JSON_VALUE,
        """
          {
            "interPodCardDefinitionId": %d
          }
          """.formatted(mangogDefinitionId).getBytes(StandardCharsets.UTF_8)
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
      long podId = createPod(eventId, organizerToken, "Pod A");
      createGroupAndGetResult(eventId, podId, organizerToken, "A1", 2);

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
                                  "timeLimitMinutes": %d
                                }
                                """.formatted(username, timeLimitMinutes)))
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
            return new GroupCreationResult(extractId(body), identifier);
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

    private long inviteCoOrganizer(long eventId,
                                   String organizerToken,
                                   String username,
                                   String... responsibilities) throws Exception {
        String responsibilitiesJson = java.util.Arrays.stream(responsibilities)
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(", "));

        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "responsibilities": [%s]
                                }
                                """.formatted(username, responsibilitiesJson)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result.getResponse().getContentAsString());
    }

    private void acceptCoOrganizerInvitation(long invitationId, String inviteeToken) throws Exception {
        mockMvc.perform(post("/api/co-organizer-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    private String loginGroup(long eventId, String identifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/group/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": %d,
                                  "identifier": "%s"
                                }
                                """.formatted(eventId, identifier)))
                .andExpect(status().isOk())
                .andReturn();

        return extractToken(result.getResponse().getContentAsString());
    }

    private long predefinedInterPodCardDefinitionId(String name) {
        return interPodCardDefinitionRepository
                .findByTypeAndNameIgnoreCase(InterPodCardDefinitionType.PREDEFINED, name)
                .orElseThrow(() -> new IllegalStateException("No existe la carta inter-pod predefinida: " + name))
                .getId();
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
