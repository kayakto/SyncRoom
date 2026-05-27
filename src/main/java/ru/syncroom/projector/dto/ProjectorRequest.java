package ru.syncroom.projector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/rooms/{roomId}/projector.
 */
@Data
public class ProjectorRequest {

    /** Projector mode: "EMBED" or "STREAM". */
    @NotBlank
    private String mode;

    /** Video URL — required for EMBED mode, null/omitted for STREAM mode. */
    private String videoUrl;

    /** Optional human-readable title for the video or stream. */
    private String videoTitle;

    /**
     * EMBED only: slot length in seconds (capped at 42). Ignored for STREAM — live slots end
     * when the host stops the projector or leaves the room.
     */
    private Integer durationSec;
}
