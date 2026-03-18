package com.tfg.gonzalo.backend.controller;

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
class EventControllerIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEventInitializesOrganizerAndGameState() throws Exception {
        String organizerToken = registerOrganizer("organizer1", "Organizador Principal", "orga@example.com", "Password123");

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
                .andExpect(jsonPath("$.configuredPlayerCount").value(8))
                .andExpect(jsonPath("$.villainHpMax").value(160))
                .andExpect(jsonPath("$.villainHpCurrent").value(160))
                .andExpect(jsonPath("$.schemeThreatTarget").value(0))
                .andExpect(jsonPath("$.timerRemainingSeconds").value(10800))
                .andExpect(jsonPath("$.organizerUsername").value("organizer1"));
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
}