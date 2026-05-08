package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.GameQueueBot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameQueueBotRepository extends JpaRepository<GameQueueBot, UUID> {

    List<GameQueueBot> findByQueue_Id(UUID queueId);

    Optional<GameQueueBot> findByQueue_IdAndBotUser_Id(UUID queueId, UUID botUserId);

    long countByQueue_Id(UUID queueId);
}
