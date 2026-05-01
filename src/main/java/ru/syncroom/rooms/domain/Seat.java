package ru.syncroom.rooms.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.users.domain.User;

import java.util.UUID;

/**
 * A physical seat in a room with normalised coordinates (0.0–1.0)
 * relative to the room's background picture.
 */
@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seats_room",     columnList = "room_id"),
        @Index(name = "idx_seats_occupied", columnList = "occupied_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** Normalised X coordinate (0.0–1.0) relative to backgroundPicture width */
    @Column(nullable = false)
    private double x;

    /** Normalised Y coordinate (0.0–1.0) relative to backgroundPicture height */
    @Column(nullable = false)
    private double y;

    /** null = free seat, non-null = the user currently sitting here */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupied_by")
    private User occupiedBy;

    /** Seat bot occupying this seat (mutually exclusive with {@link #occupiedBy} in business logic). */
    @OneToOne(mappedBy = "seat", fetch = FetchType.LAZY)
    private RoomSeatBot seatBot;
}
