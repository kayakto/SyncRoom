package ru.syncroom.projector.ws;

/**
 * Types of WebSocket events broadcast on /topic/room/{roomId}/projector.
 * Android clients switch on this field to update their UI.
 */
public enum ProjectorEventType {
    /** Projector was started (EMBED or STREAM). Payload: ProjectorStartedPayload. */
    PROJECTOR_STARTED,

    /** Projector was stopped by the host. Payload: { hostId }. */
    PROJECTOR_STOPPED,

    /** Host sent a play/pause/seek command (EMBED only). Payload: ProjectorControlPayload. */
    PROJECTOR_CONTROL,

    /** RTMP stream is now live (SRS on_publish callback). Payload: { videoUrl }. */
    STREAM_LIVE,

    /** RTMP stream ended (SRS on_unpublish callback). Payload: empty. */
    STREAM_OFFLINE
}
