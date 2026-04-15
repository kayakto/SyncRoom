package ru.syncroom.games.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.syncroom.common.config.WebSocketSecurityConfig;
import ru.syncroom.games.service.GameService;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameLobbyDisconnectListener {

    private final SimpUserRegistry userRegistry;
    private final GameService gameService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Principal user = resolveUser(event);
        if (user == null) {
            return;
        }
        UUID userId = extractUserId(user);
        if (userId == null || hasAnotherStompSessionForUser(userId, sessionId)) {
            return;
        }
        gameService.scheduleLobbyUnreadyOnDisconnect(userId);
        log.debug("Scheduled lobby unready after disconnect for user {}", userId);
    }

    private Principal resolveUser(SessionDisconnectEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        if (acc != null && acc.getUser() != null) {
            return acc.getUser();
        }
        String sessionId = event.getSessionId();
        for (SimpUser u : userRegistry.getUsers()) {
            if (u.getSession(sessionId) != null) {
                return u.getPrincipal();
            }
        }
        return null;
    }

    private static UUID extractUserId(Principal principal) {
        if (principal instanceof WebSocketSecurityConfig.StompPrincipal sp) {
            return sp.user().getId();
        }
        return null;
    }

    private boolean hasAnotherStompSessionForUser(UUID userId, String disconnectedSessionId) {
        for (SimpUser u : userRegistry.getUsers()) {
            if (!matchesUser(u.getPrincipal(), userId)) {
                continue;
            }
            for (SimpSession session : u.getSessions()) {
                if (!session.getId().equals(disconnectedSessionId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesUser(Principal principal, UUID userId) {
        if (principal instanceof WebSocketSecurityConfig.StompPrincipal sp) {
            return sp.user().getId().equals(userId);
        }
        return false;
    }
}
