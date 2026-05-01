package ru.syncroom.rooms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.rooms.domain.RoomSeatBot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomSeatBotRepository extends JpaRepository<RoomSeatBot, UUID> {

    long countByRoom_Id(UUID roomId);

    boolean existsByRoom_IdAndBotType(UUID roomId, String botType);

    List<RoomSeatBot> findByRoom_IdOrderByCreatedAtAsc(UUID roomId);

    List<RoomSeatBot> findByRoom_IdAndBotType(UUID roomId, String botType);

    Optional<RoomSeatBot> findBySeat_Id(UUID seatId);
}
