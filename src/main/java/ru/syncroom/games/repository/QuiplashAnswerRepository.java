package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.QuiplashAnswer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuiplashAnswerRepository extends JpaRepository<QuiplashAnswer, UUID> {
    List<QuiplashAnswer> findByPromptId(UUID promptId);
    Optional<QuiplashAnswer> findByPromptIdAndPlayerId(UUID promptId, UUID playerId);
    long countByPromptId(UUID promptId);
}

