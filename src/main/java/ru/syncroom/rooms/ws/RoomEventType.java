package ru.syncroom.rooms.ws;

/**
 * Types of WebSocket events broadcast on /topic/room/{roomId}.
 * Android clients switch on this field to update their UI.
 */
public enum RoomEventType {
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT
}
