package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "gartic_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GarticStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chain_id", nullable = false)
    private GarticChain chain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private GamePlayer player;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_type", nullable = false, length = 16)
    private String stepType; // TEXT, DRAWING

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}

