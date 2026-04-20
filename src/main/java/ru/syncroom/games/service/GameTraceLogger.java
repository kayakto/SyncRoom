package ru.syncroom.games.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@Slf4j
public class GameTraceLogger {

    private final boolean enabled;
    private final String dir;

    public GameTraceLogger(
            @Value("${games.trace.enabled:true}") boolean enabled,
            @Value("${games.trace.dir:/tmp/game-traces}") String dir
    ) {
        this.enabled = enabled;
        this.dir = dir;
    }

    public void trace(UUID gameId, String message) {
        if (!enabled || gameId == null) {
            return;
        }
        try {
            Path folder = Paths.get(dir);
            Files.createDirectories(folder);
            Path file = folder.resolve("game-" + gameId + ".log");
            String safe = message == null ? "" : message.replace("\r", " ").replace("\n", " ");
            String line = OffsetDateTime.now() + " " + safe + System.lineSeparator();
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write game trace: gameId={}, reason={}", gameId, e.getMessage());
        }
    }
}
