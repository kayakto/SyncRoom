package ru.syncroom.projector.ws;

import lombok.Data;

/**
 * STOMP message payload sent by the host on /app/room/{roomId}/projector/control.
 *
 * JSON format:
 * {
 *   "type": "PROJECTOR_CONTROL",
 *   "payload": {
 *     "action": "PLAY",
 *     "positionMs": 42000
 *   }
 * }
 */
@Data
public class ProjectorControlMessage {
    /** "PLAY", "PAUSE", or "SEEK". */
    private String action;
    /** Playback position in milliseconds. Required for SEEK, optional for PLAY/PAUSE. */
    private Long positionMs;
}
