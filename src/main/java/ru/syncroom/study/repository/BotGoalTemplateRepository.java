package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.study.domain.BotGoalTemplate;

import java.util.List;
import java.util.UUID;

public interface BotGoalTemplateRepository extends JpaRepository<BotGoalTemplate, UUID> {
    List<BotGoalTemplate> findByContextAndIsActiveTrue(String context);
}
