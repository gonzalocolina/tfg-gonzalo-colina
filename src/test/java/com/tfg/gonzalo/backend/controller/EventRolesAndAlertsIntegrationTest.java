package com.tfg.gonzalo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        String organizerToken = loginAdmin("evento-coorg", "Password123");

        mockMvc.perform(post("/api/events/{eventId}/roles/co-organizers", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "coorg1",
                                  "displayName": "Co Organizador",
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
    void interPodAlertsAndEventLogsAreQueryableWithFilters() throws Exception {
        long eventId = createEvent("evento-alertas", 4, 180);
        String organizerToken = loginAdmin("evento-alertas", "Password123");
        long podId = createPod(eventId, organizerToken, "Pod A");
      GroupCreationResult group = createGroupAndGetResult(eventId, podId, organizerToken, "A1", 4);
      long groupId = group.groupId();
      String representativeCode = group.representativeCode();
        String representativeToken = loginGroup(eventId, representativeCode);

        MvcResult alertResult = mockMvc.perform(post("/api/events/{eventId}/inter-pod-alerts", eventId)
                        .header("Authorization", "Bearer " + representativeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "representativeCode": "%s",
                                  "cardType": "THE_MANGOG",
                                  "status": "HP 8"
                                }
                                """.formatted(representativeCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardType").value("THE_MANGOG"))
                .andReturn();

        long alertId = extractId(alertResult.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/events/{eventId}/inter-pod-alerts/{alertId}/status", eventId, alertId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HP 4"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HP 4"));

        mockMvc.perform(get("/api/events/{eventId}/inter-pod-alerts", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardType").value("THE_MANGOG"));

        mockMvc.perform(get("/api/events/{eventId}/logs", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .param("groupId", String.valueOf(groupId))
                        .param("actionType", "INTER_POD_ALERT_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actionType").value("INTER_POD_ALERT_CREATED"));
    }

    @Test
    void optionalTimerCanBeDisabledAndGameCanStillStart() throws Exception {
        long eventId = createEvent("evento-sin-timer", 2, 0);
        String organizerToken = loginAdmin("evento-sin-timer", "Password123");

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
