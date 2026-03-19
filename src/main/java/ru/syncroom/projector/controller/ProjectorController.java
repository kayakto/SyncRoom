package ru.syncroom.projector.controller;

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
import ru.syncroom.projector.dto.ProjectorRequest;
import ru.syncroom.projector.dto.ProjectorResponse;
import ru.syncroom.projector.service.ProjectorService;
import ru.syncroom.users.domain.User;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the Projector feature.
 *
 * WebSocket:
 *   Subscribe: /topic/room/{roomId}/projector
 *   Events:    PROJECTOR_STARTED, PROJECTOR_STOPPED, PROJECTOR_CONTROL, STREAM_LIVE, STREAM_OFFLINE
 *
 * SRS callback:
 *   POST /api/projector/srs-callback  (no JWT — called by SRS media server)
 */
@Tag(name = "Projector", description = "API для проектора: совместный просмотр видео и RTMP-стримов")
@RestController
@RequiredArgsConstructor
public class ProjectorController {

    private final ProjectorService projectorService;

    /**
     * GET /api/rooms/{roomId}/projector
     *
     * Returns the current projector state for the room.
     * 404 if no projector is active.
     */
    @Operation(
            summary = "Получить состояние проектора",
            description = "Возвращает текущий ProjectorResponse для комнаты. " +
                    "rtmpUrl возвращается только хосту (для STREAM-режима)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Проектор активен"),
            @ApiResponse(responseCode = "404", description = "Проектор не включён"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @GetMapping("/api/rooms/{roomId}/projector")
    @SecurityRequirement(name = "Bearer Authentication")
    public ProjectorResponse getProjector(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return projectorService.getProjector(roomId, currentUser.getId());
    }

    /**
     * POST /api/rooms/{roomId}/projector
     *
     * Start (or take over) the projector in a room.
     * For EMBED: provide videoUrl.
     * For STREAM: server generates stream_key and HLS URL; host receives rtmpUrl.
     */
    @Operation(
            summary = "Включить проектор",
            description = "Запускает проектор. Если уже запущен — перезаписывает (хостом становится текущий пользователь). " +
                    "Рассылает WebSocket-событие PROJECTOR_STARTED всем в комнате."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Проектор запущен"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос (videoUrl не указан для EMBED)"),
            @ApiResponse(responseCode = "403", description = "Пользователь не является участником комнаты"),
            @ApiResponse(responseCode = "404", description = "Комната не найдена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @PostMapping("/api/rooms/{roomId}/projector")
    @SecurityRequirement(name = "Bearer Authentication")
    public ProjectorResponse startProjector(
            @PathVariable UUID roomId,
            @Valid @RequestBody ProjectorRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return projectorService.startProjector(roomId, currentUser.getId(), request);
    }

    /**
     * DELETE /api/rooms/{roomId}/projector
     *
     * Stop the projector. Only the current host can do this.
     */
    @Operation(
            summary = "Выключить проектор",
            description = "Останавливает проектор. Только хост может выключить. " +
                    "Рассылает WebSocket-событие PROJECTOR_STOPPED всем в комнате."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Проектор выключен"),
            @ApiResponse(responseCode = "403", description = "Вы не хост проектора"),
            @ApiResponse(responseCode = "404", description = "Проектор не активен"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @DeleteMapping("/api/rooms/{roomId}/projector")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "Bearer Authentication")
    public void stopProjector(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        projectorService.stopProjector(roomId, currentUser.getId());
    }

    /**
     * POST /api/projector/srs-callback
     *
     * HTTP callback from the SRS media server (no JWT — internal Docker network only).
     * SRS calls this on stream publish/unpublish events.
     *
     * SRS expects a JSON response: { "code": 0 }
     */
    @Operation(
            summary = "SRS callback (внутренний)",
            description = "Вызывается SRS медиасервером при начале/завершении RTMP-стрима. " +
                    "Не требует JWT — доступен только из локальной Docker-сети."
    )
    @PostMapping("/api/projector/srs-callback")
    public Map<String, Integer> srsCallback(@RequestBody Map<String, String> body) {
        String action    = body.getOrDefault("action", "");
        String streamKey = body.getOrDefault("stream", "");
        projectorService.handleSrsCallback(action, streamKey);
        // SRS requires { "code": 0 } to consider the callback successful
        return Map.of("code", 0);
    }
}
