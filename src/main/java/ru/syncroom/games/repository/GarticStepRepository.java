package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GarticStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarticStepRepository extends JpaRepository<GarticStep, UUID> {
    long countByChainGameIdAndStepNumber(UUID gameId, Integer stepNumber);
    Optional<GarticStep> findByChainIdAndStepNumber(UUID chainId, Integer stepNumber);
    List<GarticStep> findByChainIdOrderByStepNumberAsc(UUID chainId);

    @Query("""
            select distinct s from GarticStep s
            join fetch s.player p
            left join fetch p.user
            left join fetch p.botUser
            where s.chain.id = :chainId
            order by s.stepNumber asc
            """)
    List<GarticStep> findByChainIdOrderByStepNumberAscWithPlayers(@Param("chainId") UUID chainId);

    @Query("select max(gs.stepNumber) from GarticStep gs where gs.chain.game.id = :gameId")
    Integer findMaxStepByGameId(@Param("gameId") UUID gameId);
}

