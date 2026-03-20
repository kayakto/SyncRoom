package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.GameSession;

import java.util.Optional;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    Optional<GameSession> findFirstByRoomIdAndStatusNotOrderByCreatedAtDesc(UUID roomId, String status);
}

