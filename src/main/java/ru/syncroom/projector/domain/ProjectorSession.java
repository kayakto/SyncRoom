package ru.syncroom.projector.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents an active projector session in a room.
 * <p>
 * mode = "EMBED" — shared video stream via URL (VK Video, RuTube, direct MP4/HLS).
 * mode = "STREAM" — live RTMP stream via SRS media server, served as HLS.
 * <p>
 * UNIQUE(room_id) ensures only one projector is active per room at a time.
 */
@Entity
@Table(name = "projector_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectorSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    /** Projector mode: "EMBED" or "STREAM". */
    @Column(nullable = false, length = 16)
    private String mode;

    /** Video URL (EMBED) or auto-generated HLS URL (STREAM). */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "video_title", length = 500)
    private String videoTitle;

    /** Stream key for RTMP (STREAM only), e.g. "room-{roomId}". */
    @Column(name = "stream_key", length = 255)
    private String streamKey;

    /** Whether the video is currently playing (EMBED only). */
    @Column(name = "is_playing", nullable = false)
    private Boolean isPlaying;

    /** Playback position in milliseconds at the time of updatedAt (EMBED only). */
    @Column(name = "position_ms", nullable = false)
    private Long positionMs;

    /** Whether the live stream is currently active (STREAM only; updated by SRS callback). */
    @Column(name = "is_live", nullable = false)
    private Boolean isLive;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
