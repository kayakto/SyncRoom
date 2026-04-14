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
import ru.syncroom.study.dto.CreateTaskRequest;
import ru.syncroom.study.dto.LeaderboardEntryResponse;
import ru.syncroom.study.dto.TaskLikeMutationResponse;
import ru.syncroom.study.dto.TaskResponse;
import ru.syncroom.study.dto.TaskWithLikesResponse;
import ru.syncroom.study.dto.UpdateTaskRequest;
import ru.syncroom.study.service.StudyTaskService;
import ru.syncroom.users.domain.User;

import java.util.List;
import java.util.UUID;

@Tag(name = "StudyTasks", description = "Персональные таски для учебной комнаты")
@RestController
@RequiredArgsConstructor
public class StudyTaskController {

    private final StudyTaskService taskService;

    @GetMapping("/api/rooms/{roomId}/tasks/all")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Все таски всех участников комнаты (с лайками)")
    public List<TaskWithLikesResponse> getAllTasksWithLikes(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getAllTasksWithLikes(roomId, currentUser.getId());
    }

    @GetMapping("/api/rooms/{roomId}/leaderboard")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Лидерборд по лайкам на цели в комнате")
    public List<LeaderboardEntryResponse> getLeaderboard(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getLeaderboard(roomId, currentUser.getId());
    }

    @GetMapping("/api/rooms/{roomId}/tasks")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Получить таски текущего пользователя в комнате")
    public List<TaskResponse> getMyTasks(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getMyTasks(roomId, currentUser.getId());
    }

    @PostMapping("/api/rooms/{roomId}/tasks/{taskId}/like")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Лайкнуть цель другого участника")
    public TaskLikeMutationResponse likeTask(
            @PathVariable UUID roomId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.likeTask(roomId, currentUser.getId(), taskId);
    }

    @DeleteMapping("/api/rooms/{roomId}/tasks/{taskId}/like")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Убрать лайк с цели")
    public TaskLikeMutationResponse unlikeTask(
            @PathVariable UUID roomId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.unlikeTask(roomId, currentUser.getId(), taskId);
    }

    @PostMapping("/api/rooms/{roomId}/tasks")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать новый таск")
    public TaskResponse createTask(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return taskService.create(roomId, currentUser.getId(), request);
    }

    @PutMapping("/api/rooms/{roomId}/tasks/{taskId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Обновить таск (текст, статус, порядок)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Таск обновлён"),
            @ApiResponse(responseCode = "404", description = "Таск не найден или не принадлежит пользователю")
    })
    public TaskResponse updateTask(
            @PathVariable UUID roomId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return taskService.update(roomId, currentUser.getId(), taskId, request);
    }

    @DeleteMapping("/api/rooms/{roomId}/tasks/{taskId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить таск")
    public void deleteTask(
            @PathVariable UUID roomId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        taskService.delete(roomId, currentUser.getId(), taskId);
    }
}

