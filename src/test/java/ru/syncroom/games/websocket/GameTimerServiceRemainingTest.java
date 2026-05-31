package ru.syncroom.games.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameTimerService remaining seconds")
class GameTimerServiceRemainingTest {

    @Test
    @DisplayName("getRemainingSeconds возвращает остаток активного таймера")
    void returnsRemainingForScheduledTask() throws InterruptedException {
        GameTimerService timers = new GameTimerService();
        CountDownLatch ran = new CountDownLatch(1);
        String key = "test:timer";
        timers.schedule(key, 30, ran::countDown);

        int remaining = timers.getRemainingSeconds(key);
        assertTrue(remaining > 0 && remaining <= 30);

        timers.cancel(key);
        assertEquals(-1, timers.getRemainingSeconds(key));
    }

    @Test
    @DisplayName("исключение в задаче не ломает планировщик")
    void failedTaskDoesNotBreakScheduler() throws InterruptedException {
        GameTimerService timers = new GameTimerService();
        CountDownLatch failed = new CountDownLatch(1);
        timers.schedule("fail", 0, () -> {
            failed.countDown();
            throw new RuntimeException("boom");
        });
        assertTrue(failed.await(2, TimeUnit.SECONDS));

        CountDownLatch ok = new CountDownLatch(1);
        timers.schedule("ok", 0, ok::countDown);
        assertTrue(ok.await(2, TimeUnit.SECONDS));
    }
}
