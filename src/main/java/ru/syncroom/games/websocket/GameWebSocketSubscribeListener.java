package ru.syncroom.games.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import ru.syncroom.games.service.GameService;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP does not replay topic messages published before SUBSCRIBE. When a client subscribes to
 * {@code /topic/game/{gameId}} or {@code /user/topic/game/{gameId}}, send the current game phase
 * (and roster) directly to that user via {@code convertAndSendToUser}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketSubscribeListener {

    private static final String GAME_TOPIC_PREFIX = "/topic/game/";
    private static final String USER_GAME_TOPIC_PREFIX = "/user/topic/game/";

    private final GameService gameService;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String dest = acc.getDestination();
        UUID gameId = parseGameIdFromDestination(dest);
        if (gameId == null) {
            return;
        }
        Principal principal = acc.getUser();
        if (principal == null) {
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return;
        }
        try {
            gameService.replayMissedEventsOnSubscribe(gameId, userId);
        } catch (Exception e) {
            log.debug("Game subscribe replay skipped: gameId={} userId={} — {}", gameId, userId, e.getMessage());
        }
    }

    private static UUID parseGameIdFromDestination(String dest) {
        if (dest == null) {
            return null;
        }
        String gameIdRaw;
        if (dest.startsWith(USER_GAME_TOPIC_PREFIX)) {
            gameIdRaw = dest.substring(USER_GAME_TOPIC_PREFIX.length());
        } else if (dest.startsWith(GAME_TOPIC_PREFIX)) {
            gameIdRaw = dest.substring(GAME_TOPIC_PREFIX.length());
        } else {
            return null;
        }
        try {
            return UUID.fromString(gameIdRaw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
