package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.BotUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotUserRepository extends JpaRepository<BotUser, UUID> {
    List<BotUser> findByIsActiveTrueOrderByNameAsc();

    List<BotUser> findByBotTypeAndIsActiveTrueOrderByNameAsc(String botType);

    Optional<BotUser> findByIdAndIsActiveTrue(UUID id);
}
