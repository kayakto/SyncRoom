package ru.syncroom.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration.
 *
 * Endpoints:
 *   /ws              — STOMP handshake (with SockJS fallback)
 *
 * Destination prefixes:
 *   /app             — client → server (handled by @MessageMapping methods)
 *   /topic           — server → client (simple in-memory broker)
 *
 * Android client usage:
 *   val client = OkHttpClient()
 *   val stompClient = StompClient.over(WebSocket.factory(client))
 *   stompClient.connect("ws://host/ws", headers)
 *   stompClient.subscribe("/topic/room/{roomId}") { frame -> ... }
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker; messages published to /topic/* are pushed to all subscribers
        registry.enableSimpleBroker("/topic");
        // Messages sent from client to /app/* are routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint (Android, Postman, ws:// clients)
        // URL: ws://localhost:8080/ws-stomp
        registry
                .addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*");

        // SockJS fallback for web browser clients
        // URL: ws://localhost:8080/ws/websocket  (or http:// for SockJS polling)
        registry
                .addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
