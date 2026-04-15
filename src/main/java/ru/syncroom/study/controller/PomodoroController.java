package ru.syncroom.study.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.study.dto.PomodoroResponse;
import ru.syncroom.study.dto.PomodoroStartRequest;
import ru.syncroom.study.service.PomodoroService;
import ru.syncroom.users.domain.User;

import java.util.UUID;

@Tag(name = "Pomodoro", description = "Помодоро-таймер для комнат study и work")
@RestController
@RequiredArgsConstructor
public class PomodoroController {

    private final PomodoroService pomodoroService;

    @GetMapping("/api/rooms/{roomId}/pomodoro")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Текущее состояние помодоро")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Помодоро активен"),
            @ApiResponse(responseCode = "404", description = "Помодоро не запущен")
    })
    public PomodoroResponse get(
            @PathVariable UUID roomId
    ) {
        return pomodoroService.get(roomId);
    }

    @PostMapping("/api/rooms/{roomId}/pomodoro/start")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Запустить помодоро (комната study или work)")
    public PomodoroResponse start(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody(required = false) PomodoroStartRequest request
    ) {
        if (request == null) {
            request = new PomodoroStartRequest();
        }
        return pomodoroService.start(roomId, currentUser.getId(), request);
    }

    @PostMapping("/api/rooms/{roomId}/pomodoro/pause")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Поставить помодоро на паузу")
    public PomodoroResponse pause(@PathVariable UUID roomId) {
        return pomodoroService.pause(roomId);
    }

    @PostMapping("/api/rooms/{roomId}/pomodoro/resume")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Продолжить помодоро после паузы")
    public PomodoroResponse resume(@PathVariable UUID roomId) {
        return pomodoroService.resume(roomId);
    }

    @PostMapping("/api/rooms/{roomId}/pomodoro/skip")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Пропустить текущую фазу помодоро")
    public PomodoroResponse skip(@PathVariable UUID roomId) {
        return pomodoroService.skip(roomId);
    }

    @DeleteMapping("/api/rooms/{roomId}/pomodoro")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Остановить и удалить помодоро")
    public void stop(@PathVariable UUID roomId) {
        pomodoroService.stop(roomId);
    }
}

