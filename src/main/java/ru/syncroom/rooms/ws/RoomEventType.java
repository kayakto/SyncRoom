package ru.syncroom.rooms.ws;

/**
 * Types of WebSocket events broadcast on /topic/room/{roomId} and /topic/room/{roomId}/seats.
 * Android clients switch on this field to update their UI.
 */
public enum RoomEventType {
    // Participant events — /topic/room/{roomId}
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,

    // Seat events — /topic/room/{roomId}/seats
    SEAT_TAKEN,
    SEAT_LEFT
}

