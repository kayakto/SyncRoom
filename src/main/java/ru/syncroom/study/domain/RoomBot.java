package ru.syncroom.study.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.rooms.domain.Room;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_bot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomBot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "bot_type", nullable = false, length = 50)
    private String botType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(nullable = false, columnDefinition = "text")
    private String config;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
