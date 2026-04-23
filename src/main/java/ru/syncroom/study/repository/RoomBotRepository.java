package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.study.domain.RoomBot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomBotRepository extends JpaRepository<RoomBot, UUID> {
    Optional<RoomBot> findByIdAndRoom_Id(UUID botId, UUID roomId);
    Optional<RoomBot> findFirstByRoom_IdAndBotTypeOrderByCreatedAtDesc(UUID roomId, String botType);
    List<RoomBot> findByRoom_IdAndBotTypeAndIsActiveTrue(UUID roomId, String botType);
    List<RoomBot> findByRoom_Id(UUID roomId);
}
