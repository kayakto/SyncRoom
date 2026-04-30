package ru.syncroom.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {
    /**
     * Explicitly allowed web origins (REST + WebSocket handshake).
     */
    private List<String> allowedOrigins = new ArrayList<>(
            List.of("http://localhost:5173", "http://127.0.0.1:5173")
    );
}
