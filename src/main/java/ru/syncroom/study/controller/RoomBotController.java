package ru.syncroom.study.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.study.dto.ActivateMotivationalBotRequest;
import ru.syncroom.study.dto.PomodoroManagerConfigRequest;
import ru.syncroom.study.dto.RoomBotResponse;
import ru.syncroom.study.service.MotivationalGoalBotService;
import ru.syncroom.study.service.PomodoroManagerBotService;
import ru.syncroom.users.domain.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.UUID;

@Tag(name = "RoomBots", description = "Боты для комнаты (deprecated: используйте seat-bots)")
@RestController
@RequiredArgsConstructor
public class RoomBotController {

    private final MotivationalGoalBotService motivationalGoalBotService;
    private final PomodoroManagerBotService pomodoroManagerBotService;

    @PostMapping("/api/rooms/{roomId}/bots/motivational-goals/activate")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Активировать мотивационного бота целей", deprecated = true)
    public RoomBotResponse activate(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) ActivateMotivationalBotRequest request
    ) {
        RoomBotResponse response = motivationalGoalBotService.activate(roomId, currentUser.getId(), request);
        pomodoroManagerBotService.ensureDefaultActive(roomId);
        return response;
    }

    @DeleteMapping("/api/rooms/{roomId}/bots/motivational-goals/deactivate")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Деактивировать мотивационного бота целей", deprecated = true)
    public RoomBotResponse deactivate(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        RoomBotResponse response = motivationalGoalBotService.deactivate(roomId, currentUser.getId());
        pomodoroManagerBotService.deactivateAllInRoom(roomId);
        return response;
    }

    @GetMapping("/api/rooms/{roomId}/bots")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Список ботов комнаты", deprecated = true)
    public List<RoomBotResponse> getRoomBots(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return motivationalGoalBotService.list(roomId, currentUser.getId());
    }

    @PutMapping("/api/rooms/{roomId}/bots/{botId}/config")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Обновить конфиг конкретного бота комнаты", deprecated = true)
    public RoomBotResponse updateRoomBotConfig(
            @PathVariable UUID roomId,
            @PathVariable UUID botId,
            @AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) ActivateMotivationalBotRequest request
    ) {
        return motivationalGoalBotService.updateConfig(roomId, currentUser.getId(), botId, request);
    }

    @DeleteMapping("/api/rooms/{roomId}/bots/{botId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Удалить бота из комнаты", deprecated = true)
    public void deleteRoomBot(
            @PathVariable UUID roomId,
            @PathVariable UUID botId,
            @AuthenticationPrincipal User currentUser
    ) {
        motivationalGoalBotService.delete(roomId, currentUser.getId(), botId);
    }

    @PostMapping("/api/rooms/{roomId}/bots/pomodoro-manager/activate")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Активировать помодоро-менеджер бота", deprecated = true)
    public RoomBotResponse activatePomodoroManager(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) PomodoroManagerConfigRequest request
    ) {
        return pomodoroManagerBotService.activate(roomId, currentUser.getId(), request);
    }

    @PutMapping("/api/rooms/{roomId}/bots/pomodoro-manager/{botId}/config")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Обновить конфиг помодоро-менеджер бота", deprecated = true)
    public RoomBotResponse updatePomodoroManagerConfig(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID botId,
            @RequestBody(required = false) PomodoroManagerConfigRequest request
    ) {
        return pomodoroManagerBotService.updateConfig(roomId, currentUser.getId(), botId, request);
    }

    @DeleteMapping("/api/rooms/{roomId}/bots/pomodoro-manager/deactivate")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Деактивировать помодоро-менеджер бота", deprecated = true)
    public RoomBotResponse deactivatePomodoroManager(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return pomodoroManagerBotService.deactivate(roomId, currentUser.getId());
    }
}
