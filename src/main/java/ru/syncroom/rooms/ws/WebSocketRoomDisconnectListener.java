package ru.syncroom.rooms.ws;

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
import ru.syncroom.rooms.service.RoomService;

import java.security.Principal;
import java.util.UUID;

/**
 * При закрытии последнего STOMP-соединения пользователя выполняет тот же выход из комнаты,
 * что и {@link RoomService#leaveRoom}: место, участие, игровая сессия (см. {@link ru.syncroom.games.service.GameService}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRoomDisconnectListener {

    private final RoomService roomService;
    private final SimpUserRegistry userRegistry;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Principal user = resolveUser(event);
        if (user == null) {
            log.trace("STOMP disconnect: no user (sessionId={})", sessionId);
            return;
        }
        UUID userId = extractUserId(user);
        if (userId == null) {
            log.trace("STOMP disconnect: unsupported principal {}", user.getClass().getName());
            return;
        }
        if (hasAnotherStompSessionForUser(userId, sessionId)) {
            log.debug("STOMP disconnect: user {} still has other WS sessions, not leaving room", userId);
            return;
        }
        try {
            roomService.leaveRoomOnWebSocketDisconnect(userId);
            log.info("User {} left room after WebSocket disconnect (sessionId={})", userId, sessionId);
        } catch (Exception e) {
            log.warn("leaveRoomOnWebSocketDisconnect failed for user {}: {}", userId, e.getMessage());
        }
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

    /**
     * Не считаем «другими» сессию, которая сейчас отвалилась ({@code disconnectedSessionId}).
     */
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
        if (principal == null) {
            return false;
        }
        if (principal instanceof WebSocketSecurityConfig.StompPrincipal sp) {
            return sp.user().getId().equals(userId);
        }
        return false;
    }
}
