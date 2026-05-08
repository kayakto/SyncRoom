package ru.syncroom.games.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.games.dto.*;
import ru.syncroom.games.service.GameQueueService;
import ru.syncroom.users.domain.User;

import java.util.Map;
import java.util.UUID;

@Tag(name = "GameQueues", description = "Очереди на игры по типу в комнате (selection / lobby)")
@RestController
@RequiredArgsConstructor
public class GameQueueController {

    private final GameQueueService gameQueueService;

    @GetMapping("/api/rooms/{roomId}/games/queues")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Снимок всех очередей комнаты (по типу игры)")
    public Map<String, GameQueueDto> getQueues(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return gameQueueService.getQueuesSnapshot(roomId, currentUser.getId());
    }

    @PostMapping("/api/rooms/{roomId}/games/queues/{gameType}/join")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Встать в очередь на игру (при необходимости выходит из другой очереди в этой комнате)")
    public GameQueueDto join(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @AuthenticationPrincipal User currentUser
    ) {
        return gameQueueService.joinQueue(roomId, gameType, currentUser.getId());
    }

    @PostMapping("/api/rooms/{roomId}/games/queues/{gameType}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Выйти из очереди")
    public void leave(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @AuthenticationPrincipal User currentUser
    ) {
        gameQueueService.leaveQueue(roomId, gameType, currentUser.getId());
    }

    @PostMapping("/api/rooms/{roomId}/games/queues/{gameType}/ready")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Готовность в очереди")
    public GameQueueDto ready(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody GameQueueReadyRequest body
    ) {
        return gameQueueService.setReady(roomId, gameType, currentUser.getId(), body.isReady());
    }

    @PostMapping("/api/rooms/{roomId}/games/queues/{gameType}/start")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Старт игры из очереди (все люди ready, достаточно игроков)")
    public GameQueueStartResponse start(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @AuthenticationPrincipal User currentUser
    ) {
        return gameQueueService.startFromQueue(roomId, gameType, currentUser.getId());
    }

    @PostMapping("/api/rooms/{roomId}/games/queues/{gameType}/bots")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Добавить бота в очередь")
    public GameQueueDto addBot(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody GameQueueAddBotRequest body
    ) {
        return gameQueueService.addBot(roomId, gameType, currentUser.getId(), body);
    }

    @DeleteMapping("/api/rooms/{roomId}/games/queues/{gameType}/bots/{botId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Убрать бота из очереди")
    public void removeBot(
            @PathVariable UUID roomId,
            @PathVariable String gameType,
            @PathVariable UUID botId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameQueueService.removeBot(roomId, gameType, currentUser.getId(), botId);
    }
}
