package ru.syncroom.rooms.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String context; // "work", "study", "sport", "leisure"

    @Column(nullable = false)
    private String title;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "background_picture", columnDefinition = "TEXT")
    private String backgroundPicture;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}

