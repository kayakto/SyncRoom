package ru.syncroom.projector.ws;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * WebSocket event envelope broadcast to /topic/room/{roomId}/projector.
 *
 * Android STOMP subscriber receives this JSON:
 * {
 *   "type": "PROJECTOR_STARTED",
 *   "payload": { ... },
 *   "timestamp": "2026-03-18T12:00:00Z"
 * }
 *
 * payload is typed as Object so this envelope can be reused for all projector event types.
 */
@Data
@Builder
public class ProjectorEvent {

    private ProjectorEventType type;
    private Object payload;
    private OffsetDateTime timestamp;

    public static ProjectorEvent of(ProjectorEventType type, Object payload) {
        return ProjectorEvent.builder()
                .type(type)
                .payload(payload)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
