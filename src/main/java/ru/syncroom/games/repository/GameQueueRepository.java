package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GameQueue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameQueueRepository extends JpaRepository<GameQueue, UUID> {

    List<GameQueue> findByRoom_Id(UUID roomId);

    Optional<GameQueue> findByRoom_IdAndGameType(UUID roomId, String gameType);

    Optional<GameQueue> findByLinkedGameSessionId(UUID linkedGameSessionId);

    @Query("select q from GameQueue q where q.status = 'WAITING' and q.markedEmptyAt is not null and q.markedEmptyAt < :before")
    List<GameQueue> findStaleWaitingMarkedEmpty(@Param("before") OffsetDateTime before);
}
