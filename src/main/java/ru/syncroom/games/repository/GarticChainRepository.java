package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GarticChain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarticChainRepository extends JpaRepository<GarticChain, UUID> {
    List<GarticChain> findByGameId(UUID gameId);

    @Query("""
            select distinct c from GarticChain c
            join fetch c.owner o
            left join fetch o.user
            left join fetch o.botUser
            where c.game.id = :gameId
            """)
    List<GarticChain> findByGameIdWithOwnersForReveal(@Param("gameId") UUID gameId);

    Optional<GarticChain> findByGameIdAndOwnerId(UUID gameId, UUID ownerId);
}

