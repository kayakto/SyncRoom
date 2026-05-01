package ru.syncroom.rooms.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_seat_bots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomSeatBot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "bot_type", nullable = false, length = 50)
    private String botType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false, unique = true)
    private Seat seat;

    @Column(nullable = false)
    private String name;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
