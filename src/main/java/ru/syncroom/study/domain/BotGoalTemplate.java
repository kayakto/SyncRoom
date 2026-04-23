package ru.syncroom.study.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "bot_goal_template")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotGoalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String context;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
