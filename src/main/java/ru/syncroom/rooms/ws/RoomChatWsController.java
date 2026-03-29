package ru.syncroom.rooms.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.rooms.dto.ChatSendPayload;
import ru.syncroom.rooms.service.RoomChatService;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP: client sends to /app/room/{roomId}/chat, subscribers receive on /topic/room/{roomId}/chat.
 */
@Controller
@RequiredArgsConstructor
public class RoomChatWsController {

    private final RoomChatService roomChatService;

    @MessageMapping("/room/{roomId}/chat")
    public void sendChat(
            @DestinationVariable String roomId,
            @Payload ChatSendPayload payload,
            Principal principal
    ) {
        if (principal == null) {
            throw new BadRequestException("STOMP authentication required");
        }
        UUID uid = UUID.fromString(principal.getName());
        roomChatService.sendMessage(UUID.fromString(roomId), uid, payload != null ? payload.getText() : null);
    }
}
