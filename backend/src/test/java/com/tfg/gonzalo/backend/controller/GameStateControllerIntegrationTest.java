package com.tfg.gonzalo.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void villainDamageFlowTriggersMilestonesAndVictory() throws Exception {
        long eventId = createEvent("evento-daño", 4);
        String organizerToken = loginAdmin("evento-daño", "Password123");
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
        String organizerToken = loginAdmin("evento-amenaza", "Password123");
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
    void timerFlowStartPauseResumeAdjustAndTimeout() throws Exception {
        long eventId = createEvent("evento-timer", 2);
        String organizerToken = loginAdmin("evento-timer", "Password123");

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
                                  "displayName": "Mesa Pendiente"
                                }
                                """.formatted(eventCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/game-state/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("pendientes de asignación")));
    }

    @Test
    void representativeActionsAreBlockedWhilePaused() throws Exception {
        long eventId = createEvent("evento-pause-lock", 4);
        String organizerToken = loginAdmin("evento-pause-lock", "Password123");
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
    void representativeActionsAreBlockedWhileTimerIsReady() throws Exception {
        long eventId = createEvent("evento-ready-lock", 4);
        String organizerToken = loginAdmin("evento-ready-lock", "Password123");
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
                                  "displayName": "Organizador",
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
            throw new IllegalStateException("No se ha encontrado representativeCode en la respuesta.");
        }
        return matcher.group(1);
    }

        private String loginAdmin(String username, String password) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/auth/admin/login")
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
}