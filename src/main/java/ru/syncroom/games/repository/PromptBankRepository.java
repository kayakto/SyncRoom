package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.games.domain.PromptBank;

import java.util.List;
import java.util.UUID;

public interface PromptBankRepository extends JpaRepository<PromptBank, UUID> {
    List<PromptBank> findAll();
}

