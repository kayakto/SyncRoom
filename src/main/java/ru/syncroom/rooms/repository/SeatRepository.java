package ru.syncroom.rooms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.rooms.domain.Seat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /** All seats in a room (used for displaying the room state) */
    List<Seat> findByRoomId(UUID roomId);

    /**
     * Find the seat a specific user is sitting on within a room.
     * Spring Data resolves: occupiedBy → User, id → User.id
     */
    Optional<Seat> findByRoomIdAndOccupiedById(UUID roomId, UUID userId);

    /**
     * Free a user's seat in a room (called on leaveRoom / WS disconnect).
     * Returns the number of rows updated.
     */
    @Modifying
    @Query("UPDATE Seat s SET s.occupiedBy = NULL WHERE s.room.id = :roomId AND s.occupiedBy.id = :userId")
    int releaseByRoomIdAndUserId(@Param("roomId") UUID roomId, @Param("userId") UUID userId);
}

