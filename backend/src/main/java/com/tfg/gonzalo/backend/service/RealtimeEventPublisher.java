package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.InterPodCardResponse;
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
    private static final String FINAL_RESULT_NOTIFICATION_PREFIX = "EVENT_FINISHED_";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishGameStateUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications) {
        publishGameStateUpdate(eventId, operation, snapshot, notifications, null);
    }

    public void publishGameStateUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications,
                                       Long targetPodId) {
        publishGameStateUpdate(eventId, operation, snapshot, notifications, targetPodId, null, null);
    }

    public void publishGameStateUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications,
                                       Long targetPodId,
                                       InterPodCardResponse interPodCard,
                                       Long interPodCardId) {
        List<String> safeNotifications = notifications == null ? List.of() : List.copyOf(notifications);
        boolean eventFinished = snapshot != null && snapshot.finalSummary() != null;
        boolean shouldPublishGameFinished = safeNotifications.stream()
            .anyMatch(RealtimeEventPublisher::isFinalResultNotification);
        GameStateRealtimeMessage message = new GameStateRealtimeMessage();
        message.setOperation(operation);
        message.setSnapshot(snapshot);
        message.setNotifications(safeNotifications);
        message.setTargetPodId(targetPodId);
        message.setTargetGroupId(null);
        message.setInterPodCard(interPodCard);
        message.setInterPodCardId(interPodCardId);
        message.setEventFinished(eventFinished);
        message.setEmittedAt(Instant.now());

        messagingTemplate.convertAndSend(GAME_STATE_TOPIC.formatted(eventId), message);
        if (!safeNotifications.isEmpty()) {
            messagingTemplate.convertAndSend(NOTIFICATIONS_TOPIC.formatted(eventId), message);
        }
        if (shouldPublishGameFinished) {
            messagingTemplate.convertAndSend(GAME_FINISHED_TOPIC.formatted(eventId), message);
        }
    }

    private static boolean isFinalResultNotification(String notification) {
        return notification != null && notification.startsWith(FINAL_RESULT_NOTIFICATION_PREFIX);
    }

    public void publishEventOperation(Long eventId, String operation) {
        publishGameStateUpdate(eventId, operation, null, List.of());
    }

    public void publishGroupRemoved(Long eventId, Long groupId) {
        GameStateRealtimeMessage message = new GameStateRealtimeMessage();
        message.setOperation("GROUP_REMOVED");
        message.setTargetGroupId(groupId);
        message.setEmittedAt(Instant.now());
        messagingTemplate.convertAndSend(GAME_STATE_TOPIC.formatted(eventId), message);
    }
}
