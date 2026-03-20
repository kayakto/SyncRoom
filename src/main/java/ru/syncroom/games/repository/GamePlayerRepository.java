package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.GamePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, UUID> {
    List<GamePlayer> findByGameId(UUID gameId);
    Optional<GamePlayer> findByGameIdAndUserId(UUID gameId, UUID userId);
    long countByGameId(UUID gameId);
    long countByGameIdAndIsReadyTrue(UUID gameId);
}

