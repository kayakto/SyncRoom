package ru.syncroom.games.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GameEventSender {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToGame(UUID gameId, String eventType, Object payload) {
        Map<String, Object> event = Map.of(
                "type", eventType,
                "payload", payload,
                "timestamp", OffsetDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId, event);
    }

    public void sendToPlayer(UUID gameId, UUID userId, String eventType, Object payload) {
        Map<String, Object> event = Map.of(
                "type", eventType,
                "payload", payload,
                "timestamp", OffsetDateTime.now().toString()
        );
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/topic/game/" + gameId,
                event
        );
    }
}

