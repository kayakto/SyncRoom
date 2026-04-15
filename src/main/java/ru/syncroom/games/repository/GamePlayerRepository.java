package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GamePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, UUID> {
    List<GamePlayer> findByGameId(UUID gameId);
    Optional<GamePlayer> findByGameIdAndUserId(UUID gameId, UUID userId);
    long countByGameId(UUID gameId);
    long countByGameIdAndIsReadyTrue(UUID gameId);

    boolean existsByGameIdAndUserId(UUID gameId, UUID userId);

    @Query("""
            SELECT gp FROM GamePlayer gp
            WHERE gp.user.id = :userId AND gp.game.status = 'LOBBY'
            ORDER BY gp.game.createdAt DESC
            """)
    List<GamePlayer> findLobbyPlayersByUserId(@Param("userId") UUID userId);
}

