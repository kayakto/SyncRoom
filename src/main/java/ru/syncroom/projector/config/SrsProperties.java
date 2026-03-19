package ru.syncroom.projector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the SRS media server.
 * <p>
 * application.yml:
 * <pre>
 * srs:
 *   host: ${SRS_HOST:localhost}
 * </pre>
 *
 * Used to build RTMP and HLS URLs for STREAM mode projector sessions.
 */
@Data
@Component
@ConfigurationProperties(prefix = "srs")
public class SrsProperties {

    /**
     * Hostname or IP of the SRS media server, accessible from clients.
     * Default: localhost (for local development without Docker).
     */
    private String host = "localhost";
}
