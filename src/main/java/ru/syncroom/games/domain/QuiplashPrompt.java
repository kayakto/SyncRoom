package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "quiplash_prompts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuiplashPrompt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameSession game;

    @Column(nullable = false)
    private Integer round;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "time_limit", nullable = false)
    private Integer timeLimit;

    /** Запись из {@code prompt_bank}; в одной игре не повторяем id до исчерпания банка. */
    @Column(name = "prompt_bank_id")
    private UUID promptBankId;
}

