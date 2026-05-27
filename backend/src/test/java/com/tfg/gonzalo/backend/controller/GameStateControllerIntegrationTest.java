package com.tfg.gonzalo.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GameStateControllerIntegrationTest {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
        private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("\"eventCode\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REPRESENTATIVE_CODE_PATTERN = Pattern.compile("\"representativeCode\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern VILLAIN_HP_PATTERN = Pattern.compile("\"villainHpCurrent\"\\s*:\\s*(\\d+)");
        private static final Pattern THREAT_PATTERN = Pattern.compile("\"schemeThreatCurrent\"\\s*:\\s*(\\d+)");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void villainDamageFlowTriggersMilestonesAndVictory() throws Exception {
        long eventId = createEvent("evento-daño", 4);
        String organizerToken = loginOrganizer("evento-daño", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        String representativeCode = createGroupAndGetRepresentativeCode(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, representativeCode);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 40
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(40))
                .andExpect(jsonPath("$.notifications[0]").value("VILLAIN_HP_50_PERCENT"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 20
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(20))
                .andExpect(jsonPath("$.notifications[0]").value("VILLAIN_HP_25_PERCENT"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 20
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(0))
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("PLAYERS_VICTORY"))
                .andExpect(jsonPath("$.snapshot.finalSummary.cause").value("VILLAIN_HP_ZERO"));
    }

    @Test
    void threatFlowReachesTargetAndEndsInDefeat() throws Exception {
        long eventId = createEvent("evento-amenaza", 3);
        String organizerToken = loginOrganizer("evento-amenaza", "Password123");
        long podId = createPod(eventId, "Pod B", organizerToken);
        String representativeCode = createGroupAndGetRepresentativeCode(eventId, podId, "B1", 3, organizerToken);
        String representativeToken = loginGroup(eventId, representativeCode);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "threat": 2
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(2))
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("THREAT_DEFEAT"))
                .andExpect(jsonPath("$.snapshot.finalSummary.cause").value("THREAT_TARGET_REACHED"));
    }

    @Test
    void threatCanBeRemovedByRepresentativeAndDelegated() throws Exception {
        long eventId = createEvent("evento-amenaza-remove", 4);
        String organizerToken = loginOrganizer("evento-amenaza-remove", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, group.representativeCode());

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "threat": 1
                                }
                                """.formatted(group.representativeCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(1));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/remove", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "threat": 1
                                }
                                """.formatted(group.representativeCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(0));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 1
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(1));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/remove/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 1,
                                  "justification": "La mesa limpió el plan principal"
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(0));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "THREAT_REMOVED_DELEGATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("evento-amenaza-remove"))
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"))
                .andExpect(jsonPath("$[0].notes").value(containsString("limpió el plan principal")));
    }

    @Test
    void joiningByEventCodeRecalculatesThreatTarget() throws Exception {
        String organizerToken = registerOrganizer("evento-join-threat");

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evento Join Threat",
                                  "configuredPlayerCount": 8,
                                  "timeLimitMinutes": 180
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String eventBody = createEventResult.getResponse().getContentAsString();
        long eventId = extractId(eventBody);
        String eventCode = extractRequiredField(eventBody, EVENT_CODE_PATTERN, "eventCode");

        mockMvc.perform(post("/api/auth/group/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "groupLabel": "Mesa 1",
                                  "playerCount": 4
                                }
                                """.formatted(eventCode)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/{eventId}/game-state", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeThreatTarget").value(2));

        mockMvc.perform(post("/api/auth/group/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "groupLabel": "Mesa 2",
                                  "playerCount": 4
                                }
                                """.formatted(eventCode)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/{eventId}/game-state", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeThreatTarget").value(4));
    }

    @Test
    void timerFlowStartPauseResumeAdjustAndTimeout() throws Exception {
        long eventId = createEvent("evento-timer", 2);
        String organizerToken = loginOrganizer("evento-timer", "Password123");
                long podId = createPod(eventId, "Pod Timer", organizerToken);
                createGroup(eventId, podId, "T1", 2, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/pause", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/resume", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/adjust", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minutesDelta": -180
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerRemainingSeconds").value(0))
                .andExpect(jsonPath("$.snapshot.timerStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("TIMEOUT_DEFEAT"))
                .andExpect(jsonPath("$.snapshot.finalSummary.cause").value("TIMER_EXPIRED"));

        mockMvc.perform(get("/api/events/{eventId}/game-state", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventResult").value("TIMEOUT_DEFEAT"));
    }

    @Test
    void gameStateRequiresAuthentication() throws Exception {
        long eventId = createEvent("evento-auth", 2);

        mockMvc.perform(get("/api/events/{eventId}/game-state", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startGameRejectsPendingAssignments() throws Exception {
        String organizerToken = registerOrganizer("evento-pendiente");

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evento Pendiente",
                                  "configuredPlayerCount": 2,
                                  "timeLimitMinutes": 180
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String eventBody = createEventResult.getResponse().getContentAsString();
        long eventId = extractId(eventBody);
        String eventCode = extractRequiredField(eventBody, EVENT_CODE_PATTERN, "eventCode");

        mockMvc.perform(post("/api/auth/group/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                                                                                                                        "groupLabel": "Mesa Pendiente"
                                }
                                """.formatted(eventCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("pendientes de asignación")));
    }

        @Test
        void startGameRejectsEventsWithoutTables() throws Exception {
                long eventId = createEvent("evento-sin-mesas", 4);
                String organizerToken = loginOrganizer("evento-sin-mesas", "Password123");

                mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                                                .header("Authorization", "Bearer " + organizerToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("sin al menos una mesa")));
        }

        @Test
        void startGameRejectsEventsWithEmptyPods() throws Exception {
                long eventId = createEvent("evento-pod-vacio", 4);
                String organizerToken = loginOrganizer("evento-pod-vacio", "Password123");
                long podWithGroupId = createPod(eventId, "Pod A", organizerToken);
                createGroup(eventId, podWithGroupId, "A1", 4, organizerToken);
                createPod(eventId, "Pod B", organizerToken);

                mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                                                .header("Authorization", "Bearer " + organizerToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("nodos sin mesas asignadas")));
        }

    @Test
    void representativeActionsAreBlockedWhilePaused() throws Exception {
        long eventId = createEvent("evento-pause-lock", 4);
        String organizerToken = loginOrganizer("evento-pause-lock", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        String representativeCode = createGroupAndGetRepresentativeCode(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, representativeCode);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/pause", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 5
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("está pausada")));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "threat": 2
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("está pausada")));
    }

    @Test
    void delegatedCorrectionsRemainAvailableWhilePaused() throws Exception {
                long eventId = createEvent("evento-delegado-pausa", 8);
        String organizerToken = loginOrganizer("evento-delegado-pausa", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/pause", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                                                                                                                        "damage": 1,
                                  "justification": "Corregir reporte mientras la partida está pausada"
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").isNumber())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"))
                                                                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(1));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage/remove/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                                                                                                                        "damage": 1,
                                  "justification": "Deshacer parte del daño comunicado"
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"))
                                                                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(0));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                                                                                                                        "threat": 1
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"))
                                                                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(1));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/remove/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 1
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"))
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(0));
    }

    @Test
    void representativeActionsAreBlockedWhileTimerIsReady() throws Exception {
        long eventId = createEvent("evento-ready-lock", 4);
        String organizerToken = loginOrganizer("evento-ready-lock", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        String representativeCode = createGroupAndGetRepresentativeCode(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, representativeCode);

        mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("READY"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 5
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "threat": 2
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("inicie el contador")));
    }

    @Test
    void organizerCannotUseNormalTableReportEndpoint() throws Exception {
        long eventId = createEvent("evento-normal-bloqueado", 4);
        String organizerToken = loginOrganizer("evento-normal-bloqueado", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 5
                                }
                                """.formatted(group.representativeCode())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("representante")));
    }

    @Test
    void representativeReportKeepsMesaAsOriginWithoutUserActor() throws Exception {
        long eventId = createEvent("evento-log-mesa", 4);
        String organizerToken = loginOrganizer("evento-log-mesa", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, group.representativeCode());

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": 5
                                }
                                """.formatted(group.representativeCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(5));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "VILLAIN_DAMAGE_REPORTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value(nullValue()))
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"));
    }

    @Test
        void delegatedReportsAllowOmittingJustificationAndKeepTraceability() throws Exception {
        long eventId = createEvent("evento-delegado", 4);
        String organizerToken = loginOrganizer("evento-delegado", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                                                                                                                        "damage": 5
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(5));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "VILLAIN_DAMAGE_DELEGATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("evento-delegado"))
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"))
                .andExpect(jsonPath("$[0].notes").value("Registro delegado de daño al villano en nombre de la mesa."));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 1,
                                  "justification": "Representante comunicó la amenaza en voz alta"
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(1));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "THREAT_DELEGATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("evento-delegado"))
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"))
                .andExpect(jsonPath("$[0].notes").value(containsString("voz alta")));
    }

    @Test
        void manualCorrectionsAllowOmittingJustificationAndStillStoreLog() throws Exception {
        long eventId = createEvent("evento-correccion", 2);
        String organizerToken = loginOrganizer("evento-correccion", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        createGroup(eventId, podId, "A1", 2, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/events/{eventId}/game-state/villain-hp", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(12));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "VILLAIN_HP_CORRECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("evento-correccion"))
                .andExpect(jsonPath("$[0].groupIdentifier").value(nullValue()))
                .andExpect(jsonPath("$[0].notes").value("Corrección manual global de HP del villano."));
    }

    @Test
    void delegatedDamageRemovalCanReopenMetricVictory() throws Exception {
        long eventId = createEvent("evento-delegado-daño-revertido", 4);
        String organizerToken = loginOrganizer("evento-delegado-daño-revertido", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        String initialSnapshot = fetchSnapshotBody(eventId, organizerToken);
        int initialVillainHp = extractRequiredIntegerField(initialSnapshot, VILLAIN_HP_PATTERN, "villainHpCurrent");

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "damage": %d
                                }
                                """.formatted(group.id(), initialVillainHp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("PLAYERS_VICTORY"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage/remove/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "damage": %d,
                                  "justification": "La mesa había cantado daño de más"
                                }
                                """.formatted(group.id(), initialVillainHp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.snapshot.eventResult").value(nullValue()))
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(initialVillainHp))
                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(0))
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "VILLAIN_DAMAGE_REVERTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"))
                .andExpect(jsonPath("$[0].notes").value(containsString("daño de más")));
    }

    @Test
    void delegatedThreatRemovalCanReopenThreatDefeat() throws Exception {
        long eventId = createEvent("evento-delegado-amenaza-revertida", 4);
        String organizerToken = loginOrganizer("evento-delegado-amenaza-revertida", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 999
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("THREAT_DEFEAT"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/threat/remove/delegated", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": %d,
                                  "threat": 999,
                                  "justification": "La mesa había comunicado amenaza incorrecta"
                                }
                                """.formatted(group.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.snapshot.eventResult").value(nullValue()))
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(0))
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));
    }

    @Test
    void revertingDamageLogReopensFinishedGameAndMarksOriginalAsReverted() throws Exception {
        long eventId = createEvent("evento-revert-log", 4);
        String organizerToken = loginOrganizer("evento-revert-log", "Password123");
        long podId = createPod(eventId, "Pod A", organizerToken);
        GroupCreationResult group = createGroup(eventId, podId, "A1", 4, organizerToken);
        String representativeToken = loginGroup(eventId, group.representativeCode());

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.timerStatus").value("RUNNING"));

        String initialSnapshot = fetchSnapshotBody(eventId, organizerToken);
        int initialVillainHp = extractRequiredIntegerField(initialSnapshot, VILLAIN_HP_PATTERN, "villainHpCurrent");

        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "damage": %d
                                }
                                """.formatted(group.representativeCode(), initialVillainHp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("FINISHED"))
                .andExpect(jsonPath("$.snapshot.eventResult").value("PLAYERS_VICTORY"));

        long originalLogId = fetchFirstLogIdByActionType(eventId, organizerToken, "VILLAIN_DAMAGE_REPORTED");

        mockMvc.perform(post("/api/events/{eventId}/logs/{logId}/revert", eventId, originalLogId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "justification": "La mesa reportó un KO por error"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.eventStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.snapshot.eventResult").value(nullValue()))
                .andExpect(jsonPath("$.snapshot.villainHpCurrent").value(initialVillainHp))
                .andExpect(jsonPath("$.snapshot.totalReportedVillainDamage").value(0))
                .andExpect(jsonPath("$.snapshot.timerStatus").value("PAUSED"));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("actionType", "VILLAIN_DAMAGE_REVERTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupIdentifier").value("A1"))
                .andExpect(jsonPath("$[0].revertedLogId").value(originalLogId))
                .andExpect(jsonPath("$[0].notes").value(containsString("KO por error")));

        mockMvc.perform(post("/api/events/{eventId}/logs/{logId}/revert", eventId, originalLogId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ya ha sido revertido")));
    }

        @Test
        void concurrentMixedRepresentativeReportsAccumulateWithoutLostUpdates() throws Exception {
                long eventId = createEvent("evento-concurrente-burst", 40);
                String organizerToken = loginOrganizer("evento-concurrente-burst", "Password123");
                long podId = createPod(eventId, "Pod C", organizerToken);
                List<GroupCreationResult> groups = new ArrayList<>();
                List<String> representativeTokens = new ArrayList<>();

                for (int i = 1; i <= 10; i++) {
                        GroupCreationResult group = createGroup(eventId, podId, "C" + i, 4, organizerToken);
                        groups.add(group);
                        representativeTokens.add(loginGroup(eventId, group.representativeCode()));
                }

                mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                                                .header("Authorization", "Bearer " + organizerToken))
                                .andExpect(status().isOk());

                String initialSnapshot = fetchSnapshotBody(eventId, organizerToken);
                int initialVillainHp = extractRequiredIntegerField(initialSnapshot, VILLAIN_HP_PATTERN, "villainHpCurrent");
                int initialThreat = extractRequiredIntegerField(initialSnapshot, THREAT_PATTERN, "schemeThreatCurrent");
                int damageWriters = 5;
                int threatWriters = 5;
                int totalRequests = damageWriters + threatWriters;
                CyclicBarrier barrier = new CyclicBarrier(totalRequests);
                ExecutorService executor = Executors.newFixedThreadPool(totalRequests);

                try {
                        List<Future<Void>> futures = new ArrayList<>();

                        for (int i = 0; i < damageWriters; i++) {
                                GroupCreationResult group = groups.get(i);
                                String representativeToken = representativeTokens.get(i);
                                futures.add(executor.submit(() -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        mockMvc.perform(post("/api/events/{eventId}/game-state/villain-damage", eventId)
                                                                        .header("Authorization", "Bearer " + representativeToken)
                                                                        .contentType(MediaType.APPLICATION_JSON)
                                                                        .content("""
                                                                                        {
                                                                                          "representativeCode": "%s",
                                                                                          "damage": 1
                                                                                        }
                                                                                        """.formatted(group.representativeCode())))
                                                        .andExpect(status().isOk());
                                        return null;
                                }));
                        }

                        for (int i = damageWriters; i < totalRequests; i++) {
                                GroupCreationResult group = groups.get(i);
                                String representativeToken = representativeTokens.get(i);
                                futures.add(executor.submit(() -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        mockMvc.perform(post("/api/events/{eventId}/game-state/threat", eventId)
                                                                        .header("Authorization", "Bearer " + representativeToken)
                                                                        .contentType(MediaType.APPLICATION_JSON)
                                                                        .content("""
                                                                                        {
                                                                                          "representativeCode": "%s",
                                                                                          "threat": 1
                                                                                        }
                                                                                        """.formatted(group.representativeCode())))
                                                        .andExpect(status().isOk());
                                        return null;
                                }));
                        }

                        for (Future<Void> future : futures) {
                                future.get(30, TimeUnit.SECONDS);
                        }
                } finally {
                        executor.shutdownNow();
                        executor.awaitTermination(5, TimeUnit.SECONDS);
                }

                mockMvc.perform(get("/api/events/{eventId}/game-state", eventId)
                                                .header("Authorization", "Bearer " + organizerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.villainHpCurrent").value(initialVillainHp - damageWriters))
                                .andExpect(jsonPath("$.schemeThreatCurrent").value(initialThreat + threatWriters))
                                .andExpect(jsonPath("$.totalReportedVillainDamage").value(damageWriters));
        }

    private long createEvent(String username, int players) throws Exception {
        String organizerToken = registerOrganizer(username);

        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evento %s",
                                  "configuredPlayerCount": %d,
                                  "timeLimitMinutes": 180
                                }
                                """.formatted(username, players)))
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

        private long createPod(long eventId, String podName, String organizerToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/pods", eventId)
                                                .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "sortOrder": 0
                                }
                                """.formatted(podName)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result.getResponse().getContentAsString());
    }

        private String createGroupAndGetRepresentativeCode(long eventId,
                                                                                                           long podId,
                                                                                                           String identifier,
                                                                                                           int playerCount,
                                                                                                           String organizerToken) throws Exception {
                return createGroup(eventId, podId, identifier, playerCount, organizerToken).representativeCode();
        }

        private GroupCreationResult createGroup(long eventId,
                                                                                        long podId,
                                                                                        String identifier,
                                                                                        int playerCount,
                                                                                        String organizerToken) throws Exception {
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
                Matcher codeMatcher = REPRESENTATIVE_CODE_PATTERN.matcher(body);
                if (!codeMatcher.find()) {
            throw new IllegalStateException("No se ha encontrado representativeCode en la respuesta.");
        }
                return new GroupCreationResult(extractId(body), codeMatcher.group(1));
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

                Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
                if (!matcher.find()) {
                        throw new IllegalStateException("No se ha encontrado accessToken en la respuesta.");
                }
                return matcher.group(1);
        }

        private String loginGroup(long eventId, String code) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/auth/group/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                  "eventId": %d,
                                                                  "code": "%s"
                                                                }
                                                                """.formatted(eventId, code)))
                                .andExpect(status().isOk())
                                .andReturn();

                Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
                if (!matcher.find()) {
                        throw new IllegalStateException("No se ha encontrado accessToken en la respuesta.");
                }
                return matcher.group(1);
        }

        private String fetchSnapshotBody(long eventId, String organizerToken) throws Exception {
                return mockMvc.perform(get("/api/events/{eventId}/game-state", eventId)
                                                .header("Authorization", "Bearer " + organizerToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
        }

        private int extractRequiredIntegerField(String responseBody, Pattern pattern, String fieldName) {
                Matcher matcher = pattern.matcher(responseBody);
                if (!matcher.find()) {
                        throw new IllegalStateException("No se ha encontrado " + fieldName + " en la respuesta JSON.");
                }
                return Integer.parseInt(matcher.group(1));
        }

    private long extractId(String responseBody) {
        Matcher matcher = ID_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No se ha encontrado un campo id en la respuesta JSON.");
        }
        return Long.parseLong(matcher.group(1));
    }

        private String extractRequiredField(String responseBody, Pattern pattern, String fieldName) {
                Matcher matcher = pattern.matcher(responseBody);
                if (!matcher.find()) {
                        throw new IllegalStateException("No se ha encontrado " + fieldName + " en la respuesta JSON.");
                }
                return matcher.group(1);
        }

        private long fetchFirstLogIdByActionType(long eventId, String organizerToken, String actionType) throws Exception {
                String responseBody = mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                                                .header("Authorization", "Bearer " + organizerToken)
                                                .param("actionType", actionType))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                return extractId(responseBody);
        }

    private record GroupCreationResult(long id, String representativeCode) {
    }
}