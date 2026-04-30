package ru.syncroom.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP WebSocket configuration.
 *
 * Endpoints:
 *   /ws              — STOMP handshake (with SockJS fallback)
 *
 * Destination prefixes:
 *   /app             — client → server (handled by @MessageMapping methods)
 *   /topic           — broadcast (комната, игра)
 *   /user/...        — личные сообщения (например STEP_WRITE для одного игрока)
 *
 * Android client usage:
 *   val client = OkHttpClient()
 *   val stompClient = StompClient.over(WebSocket.factory(client))
 *   stompClient.connect("ws://host/ws", headers)
 *   stompClient.subscribe("/topic/room/{roomId}") { frame -> ... }
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Allow larger STOMP frames for base64 drawings sent by clients.
     * 2 MB is enough for our game limit (~2 MB image payload in GameService).
     */
    private static final int WS_MESSAGE_LIMIT_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final AppCorsProperties appCorsProperties;

    /**
     * Используем тот же ObjectMapper, что и REST (JavaTimeModule и т.д.), иначе
     * {@code convertAndSend(ChatMessageResponse)} может не сериализовать {@code OffsetDateTime} в JSON для STOMP.
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        MappingJackson2MessageConverter json = new MappingJackson2MessageConverter();
        json.setObjectMapper(objectMapper);
        json.setContentTypeResolver(m -> MimeTypeUtils.APPLICATION_JSON);
        messageConverters.add(0, json);
        return false;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic — broadcast; /queue — для user-destination (см. convertAndSendToUser в GameEventSender)
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user/");
        // Messages sent from client to /app/* are routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(WS_MESSAGE_LIMIT_BYTES);
        registry.setSendBufferSizeLimit(WS_MESSAGE_LIMIT_BYTES);
        registry.setSendTimeLimit(30_000);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint (Android, Postman, ws:// clients)
        // URL: ws://localhost:8080/ws-stomp
        registry
                .addEndpoint("/ws-stomp")
                .setAllowedOrigins(appCorsProperties.getAllowedOrigins().toArray(String[]::new));

        // SockJS fallback for web browser clients
        // URL: ws://localhost:8080/ws/websocket  (or http:// for SockJS polling)
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(appCorsProperties.getAllowedOrigins().toArray(String[]::new))
                .withSockJS();
    }
}
