package ru.syncroom.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.List;
import java.util.UUID;

/**
 * Validates JWT tokens for STOMP connections.
 *
 * Android client must send the token in the STOMP CONNECT frame header:
 *   CONNECT
 *   Authorization: Bearer <accessToken>
 *
 * After validation the Principal is set on the connection so that
 * @AuthenticationPrincipal works in @MessageMapping methods.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                // Only validate on CONNECT — the session keeps the principal for all subsequent frames
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization == null || authorization.isEmpty()) {
                        log.warn("WS CONNECT without Authorization header — anonymous connection");
                        return message;
                    }

                    String header = authorization.get(0);
                    if (!header.startsWith("Bearer ")) {
                        log.warn("WS CONNECT with malformed Authorization header");
                        return message;
                    }

                    String token = header.substring(7);
                    try {
                        UUID userId = jwtTokenService.validateTokenAndGetUserId(token);
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            // Set the fully-authenticated user as the STOMP session principal
                            accessor.setUser(new StompPrincipal(user));
                            log.debug("WS CONNECT authenticated: userId={}", userId);
                        }
                    } catch (Exception e) {
                        log.warn("WS CONNECT JWT validation failed: {}", e.getMessage());
                    }
                }
                return message;
            }
        });
    }

    /**
     * Minimal java.security.Principal wrapper around the authenticated User.
     * Allows @AuthenticationPrincipal and SimpMessageHeaderAccessor.getUser() to return the User.
     */
    public record StompPrincipal(User user) implements java.security.Principal {
        @Override
        public String getName() {
            return user.getId().toString();
        }
    }
}
