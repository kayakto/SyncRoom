package ru.syncroom.games.websocket;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class GameTimerService {

    private final Map<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public void schedule(String key, int seconds, Runnable task) {
        cancel(key);
        ScheduledFuture<?> future = scheduler.schedule(task, Math.max(seconds, 0), TimeUnit.SECONDS);
        timers.put(key, future);
    }

    public void cancel(String key) {
        ScheduledFuture<?> existing = timers.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    public void cancelRoundTimers(UUID gameId, int round) {
        cancel(answerKey(gameId, round));
        cancel(voteKey(gameId, round));
    }

    public static String answerKey(UUID gameId, int round) {
        return "game:" + gameId + ":round:" + round + ":answer";
    }

    public static String voteKey(UUID gameId, int round) {
        return "game:" + gameId + ":round:" + round + ":vote";
    }

    /** Отменяет все отложенные задачи игры (раунды, Gartic, переходы). */
    public void cancelAllForGame(UUID gameId) {
        String prefix = "game:" + gameId + ":";
        for (String key : new HashSet<>(timers.keySet())) {
            if (key.startsWith(prefix)) {
                cancel(key);
            }
        }
    }
}

