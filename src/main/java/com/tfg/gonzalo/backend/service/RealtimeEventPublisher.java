package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import com.tfg.gonzalo.backend.dto.realtime.GameStateRealtimeMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeEventPublisher {

    private static final String GAME_STATE_TOPIC = "/topic/events/%d/game-state";
    private static final String NOTIFICATIONS_TOPIC = "/topic/events/%d/notifications";
    private static final String GAME_FINISHED_TOPIC = "/topic/events/%d/game-finished";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishGameStateUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications) {
        List<String> safeNotifications = notifications == null ? List.of() : List.copyOf(notifications);
        boolean eventFinished = snapshot != null && snapshot.finalSummary() != null;
        GameStateRealtimeMessage message = new GameStateRealtimeMessage();
        message.setOperation(operation);
        message.setSnapshot(snapshot);
        message.setNotifications(safeNotifications);
        message.setEventFinished(eventFinished);
        message.setEmittedAt(Instant.now());

        messagingTemplate.convertAndSend(GAME_STATE_TOPIC.formatted(eventId), message);
        if (!safeNotifications.isEmpty()) {
            messagingTemplate.convertAndSend(NOTIFICATIONS_TOPIC.formatted(eventId), message);
        }
        if (eventFinished) {
            messagingTemplate.convertAndSend(GAME_FINISHED_TOPIC.formatted(eventId), message);
        }
    }
}
