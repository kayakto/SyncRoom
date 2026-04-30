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

@Entity
@Table(name = "projector_queue_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectorQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "video_title", length = 500)
    private String videoTitle;

    @Column(name = "requested_duration_sec")
    private Integer requestedDurationSec;

    @Column(name = "slot_duration_sec", nullable = false)
    private Integer slotDurationSec;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // WAITING, PLAYING, DONE

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
