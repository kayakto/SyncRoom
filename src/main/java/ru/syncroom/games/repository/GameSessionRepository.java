package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GameSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    Optional<GameSession> findFirstByRoomIdAndStatusNotOrderByCreatedAtDesc(UUID roomId, String status);

    Optional<GameSession> findFirstByRoomIdAndGameTypeAndStatusNotOrderByCreatedAtDesc(
            UUID roomId, String gameType, String status);

    @Query("select g from GameSession g where g.room.id = :roomId and g.status <> :finishedStatus")
    List<GameSession> findAllByRoomIdAndStatusNot(@Param("roomId") UUID roomId, @Param("finishedStatus") String finishedStatus);
}

