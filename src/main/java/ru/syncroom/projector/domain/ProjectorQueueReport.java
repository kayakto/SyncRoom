package ru.syncroom.projector.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "projector_queue_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"queue_item_id", "reporter_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectorQueueReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_item_id", nullable = false)
    private ProjectorQueueItem queueItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
