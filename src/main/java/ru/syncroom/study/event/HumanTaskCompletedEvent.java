package ru.syncroom.study.event;

import ru.syncroom.study.domain.StudyTask;

/**
 * Человек отметил свою задачу выполненной (для реакций seat-ботов, например SPORT_CHEERLEADER).
 */
public record HumanTaskCompletedEvent(StudyTask task) {
}
