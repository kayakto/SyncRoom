package ru.syncroom.study.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.study.service.PomodoroService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Серверная подстраховка: смена фазы, когда {@code phaseEndAt} уже в прошлом.
 * Работает вместе с {@link ru.syncroom.study.service.PomodoroTimerService} (таймер в памяти процесса)
 * и покрывает рестарт сервера / пропуск однопоточного таймера.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class PomodoroPhaseScheduler {

    private final PomodoroSessionRepository pomodoroSessionRepository;
    private final PomodoroService pomodoroService;

    @Scheduled(fixedRate = 1000)
    public void checkExpiredPhases() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> roomIds = pomodoroSessionRepository.findRoomIdsWithExpiredPhase(now);
        for (UUID roomId : roomIds) {
            try {
                pomodoroService.advancePhaseIfExpired(roomId);
            } catch (Exception e) {
                log.warn("Pomodoro auto phase advance failed for room {}: {}", roomId, e.getMessage());
            }
        }
    }
}
