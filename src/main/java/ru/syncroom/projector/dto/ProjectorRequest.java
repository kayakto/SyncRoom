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
     * Optional full media duration in seconds.
     * If <= 42 then item can be played fully; if > 42 playback is limited to 42 seconds.
     * If omitted, backend uses 42 seconds slot by default.
     */
    private Integer durationSec;
}
