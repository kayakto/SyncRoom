package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.syncroom.rooms.domain.Room;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "game_queues", uniqueConstraints = {
        @UniqueConstraint(name = "uq_game_queues_room_type", columnNames = {"room_id", "game_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "game_type", nullable = false, length = 32)
    private String gameType;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "WAITING";

    @Column(name = "linked_game_session_id")
    private UUID linkedGameSessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "marked_empty_at")
    private OffsetDateTime markedEmptyAt;

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GameQueuePlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "queue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GameQueueBot> bots = new ArrayList<>();

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
