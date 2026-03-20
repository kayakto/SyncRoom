package ru.syncroom.games.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "prompt_bank")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptBank {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
}

