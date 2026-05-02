package ru.syncroom.study.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.study.dto.PomodoroResponse;
import ru.syncroom.study.service.PomodoroService;
import ru.syncroom.users.domain.User;

import java.util.UUID;

@Tag(name = "Pomodoro", description = "Только чтение: участник study-комнаты получает состояние общего таймера. Старт, фазы и остановка — только на сервере (бот).")
@RestController
@RequiredArgsConstructor
public class PomodoroController {

    private final PomodoroService pomodoroService;

    @GetMapping("/api/rooms/{roomId}/pomodoro")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Текущее состояние помодоро (только участник комнаты study)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Помодоро активен"),
            @ApiResponse(responseCode = "404", description = "Помодоро не запущен")
    })
    public PomodoroResponse get(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return pomodoroService.get(roomId, currentUser.getId());
    }
}
