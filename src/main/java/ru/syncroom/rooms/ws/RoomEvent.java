package ru.syncroom.rooms.ws;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * WebSocket event envelope broadcast to /topic/room/{roomId}.
 *
 * Android STOMP subscriber receives this JSON:
 * {
 *   "type": "PARTICIPANT_JOINED",
 *   "payload": { "userId": "...", "name": "...", "avatarUrl": "...", "joinedAt": "..." },
 *   "timestamp": "2026-03-10T13:00:00+05:00"
 * }
 *
 * payload is typed as Object so we can reuse this envelope for all future event types
 * (GOAL_SET, TIMER_STARTED, LIKE_RECEIVED, etc.) without changing the Android parsing contract.
 */
@Data
@Builder
public class RoomEvent {

    private RoomEventType type;
    private Object payload;
    private OffsetDateTime timestamp;

    public static RoomEvent of(RoomEventType type, Object payload) {
        return RoomEvent.builder()
                .type(type)
                .payload(payload)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
