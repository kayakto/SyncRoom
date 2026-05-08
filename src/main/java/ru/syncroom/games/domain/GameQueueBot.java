package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "game_queue_bots", uniqueConstraints = {
        @UniqueConstraint(name = "uq_game_queue_bots_queue_bot", columnNames = {"queue_id", "bot_user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameQueueBot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private GameQueue queue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bot_user_id", nullable = false)
    private BotUser botUser;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String difficulty = "MEDIUM";
}
