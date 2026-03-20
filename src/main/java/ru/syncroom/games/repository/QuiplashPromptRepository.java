package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.QuiplashPrompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuiplashPromptRepository extends JpaRepository<QuiplashPrompt, UUID> {
    List<QuiplashPrompt> findByGameIdOrderByRoundAsc(UUID gameId);
    Optional<QuiplashPrompt> findFirstByGameIdOrderByRoundDesc(UUID gameId);
    Optional<QuiplashPrompt> findByGameIdAndRound(UUID gameId, Integer round);
    long countByGameId(UUID gameId);
}

