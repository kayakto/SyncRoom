package ru.syncroom.projector.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import ru.syncroom.projector.service.ProjectorService;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP WebSocket controller for projector control commands.
 *
 * Client sends to: /app/room/{roomId}/projector/control
 * Server broadcasts result to: /topic/room/{roomId}/projector
 *
 * Only the projector host is allowed to send control commands.
 */
@Controller
@RequiredArgsConstructor
public class ProjectorWsController {

    private final ProjectorService projectorService;

    /**
     * Handle a play/pause/seek command from the host.
     *
     * Expected payload:
     * {
     *   "action": "PLAY" | "PAUSE" | "SEEK",
     *   "positionMs": 42000
     * }
     */
    @MessageMapping("/room/{roomId}/projector/control")
    public void handleControl(
            @DestinationVariable String roomId,
            @Payload ProjectorControlMessage message,
            Principal principal
    ) {
        // Principal is set by WebSocketSecurityConfig via JwtChannelInterceptor
        UUID userId = extractUserId(principal);
        projectorService.handleControl(
                UUID.fromString(roomId),
                userId,
                message.getAction(),
                message.getPositionMs()
        );
    }

    private UUID extractUserId(Principal principal) {
        // The principal name is set to the user's UUID string by our JWT channel interceptor
        return UUID.fromString(principal.getName());
    }
}
