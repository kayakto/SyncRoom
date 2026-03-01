package ru.syncroom.points.controller;

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
import ru.syncroom.points.dto.CreatePointRequest;
import ru.syncroom.points.dto.PointResponse;
import ru.syncroom.points.service.PointService;
import ru.syncroom.users.domain.User;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing user saved locations (points).
 */
@Tag(name = "Points", description = "API для управления сохранёнными местами пользователя")
@RestController
@RequestMapping("/api/users/{userId}/points")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class PointController {

    private final PointService pointService;

    /**
     * GET /api/users/{userId}/points
     * Returns all saved locations for the given user.
     */
    @Operation(summary = "Получить все сохранённые места пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список мест"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping
    public List<PointResponse> getPoints(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User currentUser) {
        return pointService.getPoints(userId);
    }

    /**
     * POST /api/users/{userId}/points
     * Creates a new saved location.
     */
    @Operation(summary = "Создать новое место")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Место создано"),
            @ApiResponse(responseCode = "400", description = "Неверные данные"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PointResponse createPoint(
            @PathVariable UUID userId,
            @Valid @RequestBody CreatePointRequest request,
            @AuthenticationPrincipal User currentUser) {
        return pointService.createPoint(userId, request);
    }

    /**
     * PUT /api/users/{userId}/points/{pointId}
     * Updates an existing saved location.
     */
    @Operation(summary = "Обновить место")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место обновлено"),
            @ApiResponse(responseCode = "400", description = "Неверные данные"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Место не найдено")
    })
    @PutMapping("/{pointId}")
    public PointResponse updatePoint(
            @PathVariable UUID userId,
            @PathVariable UUID pointId,
            @Valid @RequestBody CreatePointRequest request,
            @AuthenticationPrincipal User currentUser) {
        return pointService.updatePoint(userId, pointId, request);
    }

    /**
     * DELETE /api/users/{userId}/points/{pointId}
     * Deletes a saved location.
     */
    @Operation(summary = "Удалить место")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Место удалено"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Место не найдено")
    })
    @DeleteMapping("/{pointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoint(
            @PathVariable UUID userId,
            @PathVariable UUID pointId,
            @AuthenticationPrincipal User currentUser) {
        pointService.deletePoint(userId, pointId);
    }
}
