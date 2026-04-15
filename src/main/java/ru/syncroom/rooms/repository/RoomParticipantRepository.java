package ru.syncroom.rooms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.RoomParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {

    int countByRoomId(UUID roomId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomParticipant> findByRoomId(UUID roomId);

    /**
     * Returns all participation records for a user (used for "current rooms"
     * query).
     */
    List<RoomParticipant> findByUserId(UUID userId);

    /** Removes a specific user from a specific room. */
    void deleteByRoomIdAndUserId(UUID roomId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE RoomParticipant p SET p.role = :role WHERE p.room.id = :roomId AND p.user.id = :userId")
    int updateRoleByRoomIdAndUserId(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("role") ParticipantRole role);
}
