package com.tfg.gonzalo.backend.controller;

import static org.hamcrest.Matchers.containsString;
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
class CoOrganizerInvitationIntegrationTest {

    private static final Pattern ID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"accessToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void organizerCanInviteExistingOrganizerAndInviteeCanAcceptAndAccessSharedEvent() throws Exception {
                String organizerToken = registerOrganizer("owner-cu13", "owner-cu13@example.com");
                String inviteeToken = registerOrganizer("shared-cu12", "shared-cu12@example.com");
        long eventId = createEvent("Evento Invitaciones", organizerToken);

        MvcResult inviteResult = mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "shared-cu12",
                                  "responsibilities": ["THREAT"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inviteeUsername").value("shared-cu12"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.responsibilities[0]").value("THREAT"))
                .andReturn();

        long invitationId = extractId(inviteResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/co-organizer-invitations/mine")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) invitationId))
                .andExpect(jsonPath("$[0].eventId").value((int) eventId))
                .andExpect(jsonPath("$[0].organizerUsername").value("owner-cu13"));

        mockMvc.perform(post("/api/co-organizer-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/events/{eventId}/roles/co-organizers", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invitationId").value((int) invitationId));

        mockMvc.perform(get("/api/events/mine")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) eventId))
                .andExpect(jsonPath("$[0].currentUserIsOwner").value(false))
                .andExpect(jsonPath("$[0].currentUserResponsibilities[0]").value("THREAT"));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) eventId))
                .andExpect(jsonPath("$.currentUserIsOwner").value(false));

        mockMvc.perform(patch("/api/events/{eventId}/game-state/threat", eventId)
                        .header("Authorization", "Bearer " + inviteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.schemeThreatCurrent").value(7));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("permiso")));
    }

    @Test
    void inviteeCanRejectInvitationAndItDisappearsFromInbox() throws Exception {
                String organizerToken = registerOrganizer("owner-reject", "owner-reject@example.com");
                String inviteeToken = registerOrganizer("reject-user", "reject-user@example.com");
        long eventId = createEvent("Evento Rechazo", organizerToken);

        long invitationId = inviteUser(eventId, organizerToken, "reject-user");

        mockMvc.perform(post("/api/co-organizer-invitations/{invitationId}/reject", invitationId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/co-organizer-invitations/mine")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void organizerCanRevokePendingInvitation() throws Exception {
                String organizerToken = registerOrganizer("owner-revoke", "owner-revoke@example.com");
                String inviteeToken = registerOrganizer("revoke-user", "revoke-user@example.com");
        long eventId = createEvent("Evento Revocacion", organizerToken);

        long invitationId = inviteUser(eventId, organizerToken, "revoke-user");

        mockMvc.perform(delete("/api/events/{eventId}/roles/co-organizer-invitations/{invitationId}", eventId, invitationId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/co-organizer-invitations/mine")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void organizerCanRevokeAcceptedInvitationBeforeGameStarts() throws Exception {
                String organizerToken = registerOrganizer("owner-revoke-accepted", "owner-revoke-accepted@example.com");
                String inviteeToken = registerOrganizer("accepted-user", "accepted-user@example.com");
        long eventId = createEvent("Evento Revocacion Aceptada", organizerToken);

        long invitationId = inviteUser(eventId, organizerToken, "accepted-user");

        mockMvc.perform(post("/api/co-organizer-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(delete("/api/events/{eventId}/roles/co-organizer-invitations/{invitationId}", eventId, invitationId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/mine")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotRevokeAcceptedInvitationAfterGameStarts() throws Exception {
                String organizerToken = registerOrganizer("owner-started-revoke", "owner-started-revoke@example.com");
                String inviteeToken = registerOrganizer("started-user", "started-user@example.com");
        long eventId = createEvent("Evento Revocacion Tras Inicio", organizerToken);

        long invitationId = inviteUser(eventId, organizerToken, "started-user");

        mockMvc.perform(post("/api/co-organizer-invitations/{invitationId}/accept", invitationId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/events/{eventId}/game-state/timer/start", eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/events/{eventId}/roles/co-organizer-invitations/{invitationId}", eventId, invitationId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("antes de iniciar la partida")));
    }

    @Test
    void organizerCannotInviteUnknownUsersDuplicatesOrExceedAvailableSlots() throws Exception {
                String organizerToken = registerOrganizer("owner-validaciones", "owner-validaciones@example.com");
                registerOrganizer("slot-a", "slot-a@example.com");
                registerOrganizer("slot-b", "slot-b@example.com");
                registerOrganizer("slot-c", "slot-c@example.com");
                registerOrganizer("slot-d", "slot-d@example.com");
        long eventId = createEvent("Evento Validaciones", organizerToken);

        mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "no-existe",
                                  "responsibilities": ["THREAT"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No existe ninguna cuenta registrada")));

        inviteUser(eventId, organizerToken, "slot-a");

        mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "slot-a",
                                  "responsibilities": ["TIMER"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Ya existe una invitación pendiente")));

        inviteUser(eventId, organizerToken, "slot-b");
        inviteUser(eventId, organizerToken, "slot-c");

        mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "slot-d",
                                  "responsibilities": ["THREAT"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No quedan plazas disponibles")));
    }

    private long inviteUser(long eventId, String organizerToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/roles/co-organizer-invitations", eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "responsibilities": ["THREAT"]
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result.getResponse().getContentAsString());
    }

    private long createEvent(String eventName, String organizerToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "configuredPlayerCount": 4,
                                  "timeLimitMinutes": 180
                                }
                                """.formatted(eventName)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result.getResponse().getContentAsString());
    }

                private String registerOrganizer(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "Password123"
                                }
                                                                                                                                """.formatted(username, email)))
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