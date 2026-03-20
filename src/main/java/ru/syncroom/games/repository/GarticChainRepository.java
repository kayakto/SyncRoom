package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.GarticChain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarticChainRepository extends JpaRepository<GarticChain, UUID> {
    List<GarticChain> findByGameId(UUID gameId);
    Optional<GarticChain> findByGameIdAndOwnerId(UUID gameId, UUID ownerId);
}

