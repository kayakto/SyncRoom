package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.syncroom.users.domain.User;

import java.util.UUID;

@Entity
@Table(name = "game_players")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameSession game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_ready", nullable = false)
    private Boolean isReady;

    @Column(nullable = false)
    private Integer score;
}

