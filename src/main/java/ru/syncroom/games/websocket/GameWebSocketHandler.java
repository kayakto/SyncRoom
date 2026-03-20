package ru.syncroom.games.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import ru.syncroom.games.dto.GameActionMessage;
import ru.syncroom.games.service.GameService;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameWebSocketHandler {

    private final GameService gameService;

    @MessageMapping("/game/{gameId}/action")
    public void handleAction(
            @DestinationVariable String gameId,
            @Payload GameActionMessage message,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        gameService.handleAction(UUID.fromString(gameId), userId, message);
    }
}

