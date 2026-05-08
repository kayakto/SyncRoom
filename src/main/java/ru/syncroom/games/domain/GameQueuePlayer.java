package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_queue_players", uniqueConstraints = {
        @UniqueConstraint(name = "uq_game_queue_players_queue_user", columnNames = {"queue_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameQueuePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private GameQueue queue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_ready", nullable = false)
    @Builder.Default
    private Boolean isReady = false;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = OffsetDateTime.now();
    }
}
