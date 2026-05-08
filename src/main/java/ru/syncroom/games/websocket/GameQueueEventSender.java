package ru.syncroom.games.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Room-scoped game queue events (selection / lobby before per-game WebSocket).
 * Destination: {@code /topic/room/{roomId}/games}
 */
@Component
@RequiredArgsConstructor
public class GameQueueEventSender {

    private final SimpMessagingTemplate messagingTemplate;

    public void send(UUID roomId, String eventType, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("payload", payload);
        event.put("timestamp", OffsetDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/games", event);
    }

    public static String topic(UUID roomId) {
        return "/topic/room/" + roomId + "/games";
    }
}
