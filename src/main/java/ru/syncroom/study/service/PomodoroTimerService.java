package ru.syncroom.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PomodoroTimerService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public void schedulePhaseEnd(UUID roomId, long seconds, Runnable onTimeout) {
        cancel(roomId);
        if (seconds <= 0) {
            scheduler.execute(onTimeout);
            return;
        }
        ScheduledFuture<?> future = scheduler.schedule(onTimeout, seconds, TimeUnit.SECONDS);
        activeTimers.put(roomId, future);
        log.debug("Pomodoro timer scheduled for room {} in {} seconds", roomId, seconds);
    }

    public void cancel(UUID roomId) {
        ScheduledFuture<?> existing = activeTimers.remove(roomId);
        if (existing != null) {
            existing.cancel(false);
            log.debug("Pomodoro timer cancelled for room {}", roomId);
        }
    }
}

