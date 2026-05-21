package com.tfg.gonzalo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tfg.gonzalo.backend.dto.realtime.GameStateRealtimeMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RealtimeEventPublisherTest {

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
}
