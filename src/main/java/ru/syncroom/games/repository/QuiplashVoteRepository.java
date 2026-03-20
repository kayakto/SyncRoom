package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.QuiplashVote;

import java.util.Optional;
import java.util.UUID;

public interface QuiplashVoteRepository extends JpaRepository<QuiplashVote, UUID> {
    long countByPromptId(UUID promptId);
    Optional<QuiplashVote> findByPromptIdAndVoterId(UUID promptId, UUID voterId);
}

