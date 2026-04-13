package ru.syncroom.rooms.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.syncroom.common.config.WebSocketSecurityConfig;
import ru.syncroom.rooms.service.RoomService;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketRoomDisconnectListener")
class WebSocketRoomDisconnectListenerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private SimpUserRegistry userRegistry;

    @InjectMocks
    private WebSocketRoomDisconnectListener listener;

    private User user;
    private WebSocketSecurityConfig.StompPrincipal principal;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .name("U")
                .email("u@test.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build();
        principal = new WebSocketSecurityConfig.StompPrincipal(user);
    }

    @Test
    @DisplayName("последняя сессия — вызывает leaveRoomOnWebSocketDisconnect")
    void lastSession_triggersLeave() {
        String sessionId = "sess-a";
        SessionDisconnectEvent event = disconnectEvent(sessionId, principal);

        SimpUser otherUser = mock(SimpUser.class);
        when(otherUser.getPrincipal()).thenReturn(mock(Principal.class));
        when(userRegistry.getUsers()).thenReturn(Set.of(otherUser));

        listener.onDisconnect(event);

        verify(roomService, times(1)).leaveRoomOnWebSocketDisconnect(user.getId());
    }

    @Test
    @DisplayName("есть вторая STOMP-сессия того же пользователя — комнату не покидаем")
    void secondSession_doesNotLeave() {
        String sessionA = "sess-a";
        String sessionB = "sess-b";
        SessionDisconnectEvent event = disconnectEvent(sessionB, principal);

        SimpSession sessionMock = mock(SimpSession.class);
        when(sessionMock.getId()).thenReturn(sessionA);

        SimpUser simpUser = mock(SimpUser.class);
        when(simpUser.getPrincipal()).thenReturn(principal);
        when(simpUser.getSessions()).thenReturn(Set.of(sessionMock));

        when(userRegistry.getUsers()).thenReturn(Set.of(simpUser));

        listener.onDisconnect(event);

        verify(roomService, never()).leaveRoomOnWebSocketDisconnect(any());
    }

    @Test
    @DisplayName("без principal — ничего не делаем")
    void noPrincipal_noLeave() {
        SessionDisconnectEvent event = disconnectEvent("sess-x", null);
        when(userRegistry.getUsers()).thenReturn(Collections.emptySet());

        listener.onDisconnect(event);

        verifyNoInteractions(roomService);
    }

    private static SessionDisconnectEvent disconnectEvent(String sessionId, Principal user) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        acc.setSessionId(sessionId);
        if (user != null) {
            acc.setUser(user);
        }
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        return new SessionDisconnectEvent(new Object(), message, sessionId, CloseStatus.NORMAL);
    }
}
