package com.tfg.gonzalo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tfg.gonzalo.backend.dto.gamestate.FinalSummaryResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import com.tfg.gonzalo.backend.dto.realtime.GameStateRealtimeMessage;
import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GameFinishCause;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RealtimeEventPublisherTest {

    @Test
    void doesNotRepublishGameFinishedTopicForFinishedSnapshotsWithoutNewFinalNotification() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        RealtimeEventPublisher publisher = new RealtimeEventPublisher(messagingTemplate);

        publisher.publishGameStateUpdate(42L,
                "GET_SNAPSHOT",
                finishedSnapshot(EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO),
                List.of());

        ArgumentCaptor<GameStateRealtimeMessage> gameStateMessageCaptor = ArgumentCaptor.forClass(GameStateRealtimeMessage.class);

        verify(messagingTemplate).convertAndSend(eq("/topic/events/42/game-state"), gameStateMessageCaptor.capture());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/events/42/notifications"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/events/42/game-finished"), any(Object.class));

        assertEquals(true, gameStateMessageCaptor.getValue().isEventFinished());
        assertEquals("GET_SNAPSHOT", gameStateMessageCaptor.getValue().getOperation());
    }

    @Test
    void publishesGameFinishedTopicWhenFinalResultNotificationIsPresent() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        RealtimeEventPublisher publisher = new RealtimeEventPublisher(messagingTemplate);

        publisher.publishGameStateUpdate(42L,
                "REPORT_VILLAIN_DAMAGE",
                finishedSnapshot(EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO),
                List.of("EVENT_FINISHED_PLAYERS_VICTORY"));

        ArgumentCaptor<GameStateRealtimeMessage> gameFinishedCaptor = ArgumentCaptor.forClass(GameStateRealtimeMessage.class);

        verify(messagingTemplate).convertAndSend(eq("/topic/events/42/game-finished"), gameFinishedCaptor.capture());
        assertEquals(List.of("EVENT_FINISHED_PLAYERS_VICTORY"), gameFinishedCaptor.getValue().getNotifications());
        assertEquals(true, gameFinishedCaptor.getValue().isEventFinished());
    }

    @Test
    void includesTargetPodIdInPodScopedRealtimeMessages() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        RealtimeEventPublisher publisher = new RealtimeEventPublisher(messagingTemplate);

        publisher.publishGameStateUpdate(42L,
                "INTER_POD_CARD_CREATED",
                null,
                List.of("INTER_POD_CARD_CREATED"),
                7L);

        ArgumentCaptor<GameStateRealtimeMessage> gameStateMessageCaptor = ArgumentCaptor.forClass(GameStateRealtimeMessage.class);
        ArgumentCaptor<GameStateRealtimeMessage> notificationsMessageCaptor = ArgumentCaptor.forClass(GameStateRealtimeMessage.class);

        verify(messagingTemplate).convertAndSend(eq("/topic/events/42/game-state"), gameStateMessageCaptor.capture());
        verify(messagingTemplate).convertAndSend(eq("/topic/events/42/notifications"), notificationsMessageCaptor.capture());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/events/42/game-finished"), any(Object.class));

        assertEquals(7L, gameStateMessageCaptor.getValue().getTargetPodId());
        assertEquals("INTER_POD_CARD_CREATED", gameStateMessageCaptor.getValue().getOperation());
        assertEquals(List.of("INTER_POD_CARD_CREATED"), gameStateMessageCaptor.getValue().getNotifications());
        assertEquals(7L, notificationsMessageCaptor.getValue().getTargetPodId());
    }

    private GameStateSnapshotResponse finishedSnapshot(EventResult result, GameFinishCause cause) {
        Instant now = Instant.parse("2026-05-27T10:15:30Z");
        return new GameStateSnapshotResponse(
                42L,
                EventStatus.FINISHED,
                result,
                "Villano",
            VillainDefinitionType.PREDEFINED,
                0,
                100,
                0,
                16,
                0,
                TimerStatus.FINISHED,
                100,
                now.minusSeconds(3600),
                now,
                now,
                new FinalSummaryResponse(result, cause, 3600L, 100, 0)
        );
    }
}
