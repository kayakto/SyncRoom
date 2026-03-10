package ru.syncroom.rooms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.rooms.dto.JoinRoomResponse;
import ru.syncroom.rooms.dto.RoomResponse;
import ru.syncroom.rooms.service.RoomService;
import ru.syncroom.users.domain.User;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for room management.
 *
 * WebSocket:
 *   STOMP endpoint: ws://host/ws
 *   Subscribe:      /topic/room/{roomId}
 *   Events:         PARTICIPANT_JOINED, PARTICIPANT_LEFT  (more in Step 6)
 */
@Tag(name = "Rooms", description = "API для работы с комнатами")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoomController {

    private final RoomService roomService;

    /** GET /api/rooms — все комнаты */
    @Operation(summary = "Получить список всех комнат")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список комнат"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @GetMapping
    public List<RoomResponse> getRooms() {
        return roomService.getAllRooms();
    }

    /** GET /api/rooms/my — комнаты текущего пользователя */
    @Operation(summary = "Комнаты текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список комнат пользователя"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @GetMapping("/my")
    public List<RoomResponse> getMyRooms(@AuthenticationPrincipal User currentUser) {
        return roomService.getUserRooms(currentUser.getId());
    }

    /**
     * POST /api/rooms/{roomId}/join
     *
     * Joins a room. Returns the room state and full participant list.
     * Also broadcasts PARTICIPANT_JOINED on /topic/room/{roomId}.
     * If the room is full, a duplicate is created and the user joins that.
     */
    @Operation(
            summary = "Войти в комнату",
            description = "Возвращает { room, participants[] }. " +
                    "Одновременно рассылает WebSocket-событие PARTICIPANT_JOINED " +
                    "всем подписчикам /topic/room/{roomId}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно присоединился"),
            @ApiResponse(responseCode = "400", description = "Пользователь уже в другой комнате"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Комната не найдена")
    })
    @PostMapping("/{roomId}/join")
    public JoinRoomResponse joinRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return roomService.joinRoom(roomId, currentUser.getId());
    }

    /**
     * POST /api/rooms/{roomId}/leave
     *
     * Leaves a room. Broadcasts PARTICIPANT_LEFT on /topic/room/{roomId}.
     * If the room was full and now has space it becomes active again.
     */
    @Operation(
            summary = "Покинуть комнату",
            description = "Рассылает WebSocket-событие PARTICIPANT_LEFT " +
                    "всем подписчикам /topic/room/{roomId}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Успешно покинул комнату"),
            @ApiResponse(responseCode = "400", description = "Пользователь не состоит в этой комнате"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Комната не найдена")
    })
    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        roomService.leaveRoom(roomId, currentUser.getId());
    }
}
