package com.tfg.gonzalo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class EventSetupControllerIntegrationTest {

        private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
        private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("\"eventCode\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern GROUP_ID_PATTERN = Pattern.compile("\"groupId\"\\s*:\\s*(\\d+)");
        private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createPodAndGroupUpdatesStructureAndThreatTarget() throws Exception {
        String organizerToken = registerOrganizer("setupadmin", "Setup Admin", "setup@example.com", "Password123");

        String eventRequest = """
                {
                  "name": "Evento Setup",
                  "configuredPlayerCount": 6,
                  "timeLimitMinutes": 180
                }
                """;

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest))
                .andExpect(status().isCreated())
                .andReturn();

        long eventId = extractId(createEventResult.getResponse().getContentAsString());

        String podRequest = """
                {
                  \"name\": \"Pod A\",
                  \"sortOrder\": 0
                }
                """;

        MvcResult createPodResult = mockMvc.perform(post("/api/events/{eventId}/pods", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(podRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pod A"))
                .andReturn();

        long podId = extractId(createPodResult.getResponse().getContentAsString());

        String groupRequest = """
                {
                  \"identifier\": \"A1\",
                  \"playerCount\": 3
                }
                """;

        mockMvc.perform(post("/api/events/{eventId}/pods/{podId}/groups", eventId, podId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier").value("A1"))
                .andExpect(jsonPath("$.playerCount").value(3));

        mockMvc.perform(get("/api/events/{eventId}/structure", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredPlayerCount").value(3))
                .andExpect(jsonPath("$.currentGroupCount").value(1))
                .andExpect(jsonPath("$.assignedPlayerCount").value(3))
                .andExpect(jsonPath("$.unassignedGroupCount").value(0))
                .andExpect(jsonPath("$.unassignedPlayerCount").value(0))
                .andExpect(jsonPath("$.schemeThreatTarget").value(2))
                .andExpect(jsonPath("$.pods[0].name").value("Pod A"))
                .andExpect(jsonPath("$.pods[0].groups[0].identifier").value("A1"))
                .andExpect(jsonPath("$.unassignedGroups").isEmpty());
    }

    @Test
    void playerCanJoinByEventCodeAndOrganizerCanAssignPod() throws Exception {
        String organizerToken = registerOrganizer("setupjoin", "Setup Join", "setupjoin@example.com", "Password123");

        String eventRequest = """
                {
                  "name": "Evento Join",
                  "configuredPlayerCount": 4,
                  "timeLimitMinutes": 180
                }
                """;

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String createEventBody = createEventResult.getResponse().getContentAsString();
        long eventId = extractId(createEventBody);
        String eventCode = extractRequiredField(createEventBody, EVENT_CODE_PATTERN, "eventCode");

        String podRequest = """
                {
                  "name": "Pod Norte",
                  "sortOrder": 0
                }
                """;

        MvcResult createPodResult = mockMvc.perform(post("/api/events/{eventId}/pods", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(podRequest))
                .andExpect(status().isCreated())
                .andReturn();

        long podId = extractId(createPodResult.getResponse().getContentAsString());

        MvcResult joinResult = mockMvc.perform(post("/api/auth/group/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "displayName": "Jugador Uno"
                                }
                                """.formatted(eventCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GROUP_REPRESENTATIVE"))
                .andExpect(jsonPath("$.displayName").value("Jugador Uno"))
                .andReturn();

        String joinBody = joinResult.getResponse().getContentAsString();
        long groupId = Long.parseLong(extractRequiredField(joinBody, GROUP_ID_PATTERN, "groupId"));

        mockMvc.perform(get("/api/events/{eventId}/structure", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredPlayerCount").value(1))
                .andExpect(jsonPath("$.currentGroupCount").value(1))
                .andExpect(jsonPath("$.assignedPlayerCount").value(0))
                .andExpect(jsonPath("$.unassignedGroupCount").value(1))
                .andExpect(jsonPath("$.unassignedPlayerCount").value(1))
                .andExpect(jsonPath("$.pods[0].name").value("Pod Norte"))
                .andExpect(jsonPath("$.unassignedGroups[0].identifier").value("Jugador Uno"));

        mockMvc.perform(patch("/api/events/{eventId}/groups/{groupId}/pod", eventId, groupId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "podId": %d
                                }
                                """.formatted(podId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podId").value((int) podId))
                .andExpect(jsonPath("$.identifier").value("Jugador Uno"));

        mockMvc.perform(get("/api/events/{eventId}/structure", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredPlayerCount").value(1))
                .andExpect(jsonPath("$.assignedPlayerCount").value(1))
                .andExpect(jsonPath("$.unassignedGroupCount").value(0))
                .andExpect(jsonPath("$.pods[0].name").value("Pod Norte"))
                .andExpect(jsonPath("$.pods[0].groups[0].identifier").value("Jugador Uno"));
    }

    @Test
    void organizerCanRenamePodsAndTablesAndDeletePodsReturningTablesToPending() throws Exception {
        String organizerToken = registerOrganizer("setuprename", "Setup Rename", "setuprename@example.com", "Password123");

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evento Rename",
                                  "configuredPlayerCount": 4,
                                  "timeLimitMinutes": 180
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        long eventId = extractId(createEventResult.getResponse().getContentAsString());

        long podId = extractId(mockMvc.perform(post("/api/events/{eventId}/pods", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pod Inicial",
                                  "sortOrder": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        long groupId = extractId(mockMvc.perform(post("/api/events/{eventId}/pods/{podId}/groups", eventId, podId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "Mesa A",
                                  "playerCount": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(patch("/api/events/{eventId}/pods/{podId}", eventId, podId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pod Renombrado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pod Renombrado"));

        mockMvc.perform(patch("/api/events/{eventId}/groups/{groupId}", eventId, groupId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "Mesa Omega",
                                  "playerCount": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("Mesa Omega"))
                .andExpect(jsonPath("$.playerCount").value(2));

        mockMvc.perform(patch("/api/events/{eventId}/groups/{groupId}/pod", eventId, groupId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "podId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podId").doesNotExist())
                .andExpect(jsonPath("$.identifier").value("Mesa Omega"))
                .andExpect(jsonPath("$.playerCount").value(2));

        mockMvc.perform(delete("/api/events/{eventId}/pods/{podId}", eventId, podId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{eventId}/structure", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredPlayerCount").value(2))
                .andExpect(jsonPath("$.assignedPlayerCount").value(0))
                .andExpect(jsonPath("$.unassignedGroupCount").value(1))
                .andExpect(jsonPath("$.pods").isEmpty())
                .andExpect(jsonPath("$.unassignedGroups[0].identifier").value("Mesa Omega"))
                .andExpect(jsonPath("$.unassignedGroups[0].playerCount").value(2));
    }

        private String registerOrganizer(String username, String displayName, String email, String password) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                                                {
                                                                  "username": "%s",
                                                                  "displayName": "%s",
                                                                  "email": "%s",
                                                                  "password": "%s"
                                                                }
                                                                """.formatted(username, displayName, email, password)))
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

        private String extractRequiredField(String responseBody, Pattern pattern, String fieldName) {
                Matcher matcher = pattern.matcher(responseBody);
                if (!matcher.find()) {
                        throw new IllegalStateException("No se ha encontrado " + fieldName + " en la respuesta JSON.");
                }
                return matcher.group(1);
        }
}