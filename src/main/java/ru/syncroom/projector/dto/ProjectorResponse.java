package ru.syncroom.projector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Response body for all Projector REST endpoints.
 * <p>
 * Fields specific to EMBED: isPlaying, positionMs.
 * Fields specific to STREAM: streamKey, rtmpUrl, isLive.
 * rtmpUrl is only populated for the session host.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectorResponse {

    private String id;
    private String roomId;

    /** Host of the projector — the user who started it. */
    private UserDto host;

    /** "EMBED" or "STREAM". */
    private String mode;

    /** Video URL (EMBED) or auto-generated HLS URL (STREAM). */
    private String videoUrl;

    private String videoTitle;

    /** SRS stream key. Only present for STREAM sessions. */
    private String streamKey;

    /**
     * RTMP URL for OBS/camera. Only present for STREAM sessions AND only returned to the host.
     * Viewers do not receive this field.
     */
    private String rtmpUrl;

    @JsonProperty("isPlaying")
    private boolean isPlaying;

    /** Playback position in milliseconds at updatedAt (EMBED only). */
    private long positionMs;

    @JsonProperty("isLive")
    private boolean isLive;

    /** ISO-8601 timestamp of the last state update. */
    private String updatedAt;
}
