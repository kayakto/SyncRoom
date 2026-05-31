package ru.syncroom.games.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import ru.syncroom.games.service.GameService;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameWebSocketSubscribeListener")
class GameWebSocketSubscribeListenerTest {

    @Mock
    private GameService gameService;

    @InjectMocks
    private GameWebSocketSubscribeListener listener;

    private UUID gameId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("broadcast /topic/game/{id} триггерит replay")
    void broadcastTopicTriggersReplay() {
        publishSubscribe("/topic/game/" + gameId);
        verify(gameService, timeout(500)).replayMissedEventsOnSubscribe(gameId, userId);
    }

    @Test
    @DisplayName("user /user/topic/game/{id} триггерит replay")
    void userTopicTriggersReplay() {
        publishSubscribe("/user/topic/game/" + gameId);
        verify(gameService, timeout(500)).replayMissedEventsOnSubscribe(gameId, userId);
    }

    @Test
    @DisplayName("чужой destination игнорируется")
    void unrelatedDestinationIgnored() {
        publishSubscribe("/topic/room/" + gameId);
        verifyNoInteractions(gameService);
    }

    private void publishSubscribe(String destination) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination(destination);
        acc.setUser((Principal) () -> userId.toString());
        acc.setLeaveMutable(true);
        listener.onSubscribe(new SessionSubscribeEvent(this, MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders())));
    }
}
