package ru.syncroom.games.websocket;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import ru.syncroom.games.service.GameService;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * STOMP does not replay topic messages published before SUBSCRIBE. When a client subscribes to
 * {@code /topic/game/{gameId}} or {@code /user/topic/game/{gameId}}, send the current game phase
 * (and roster) directly to that user via {@code convertAndSendToUser}.
 *
 * <p><b>Why the replay is deferred:</b> Spring publishes {@link SessionSubscribeEvent} on the
 * inbound-message thread <i>before</i> the broker registers the subscription in its registry.
 * A {@code convertAndSendToUser} issued synchronously from this listener therefore finds no
 * matching subscription yet and is silently dropped — which is exactly why the first Quiplash
 * phase ({@code PROMPT_RECEIVED}) never reached the client and the screen hung until the 60s
 * answer-timeout. Scheduling the replay a few hundred ms later lets the subscription register
 * first, so the message is delivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketSubscribeListener {

    private static final String GAME_TOPIC_PREFIX = "/topic/game/";
    private static final String USER_GAME_TOPIC_PREFIX = "/user/topic/game/";
    private static final long REPLAY_DELAY_MS = 200L;

    private final GameService gameService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-subscribe-replay");
        t.setDaemon(true);
        return t;
    });

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
        // Defer: the broker has not registered this subscription yet (see class javadoc).
        scheduler.schedule(() -> {
            try {
                gameService.replayMissedEventsOnSubscribe(gameId, userId);
            } catch (Exception e) {
                log.debug("Game subscribe replay skipped: gameId={} userId={} — {}", gameId, userId, e.getMessage());
            }
        }, REPLAY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
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
