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
import ru.syncroom.rooms.dto.RoomResponse;
import ru.syncroom.rooms.service.RoomService;
import ru.syncroom.users.domain.User;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for room management.
 */
@Tag(name = "Rooms", description = "API для работы с комнатами")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoomController {

        private final RoomService roomService;

        /**
         * GET /api/rooms
         * Returns all rooms (active and inactive). Android client filters by isActive.
         */
        @Operation(summary = "Получить список всех комнат", description = "Возвращает все комнаты с текущим количеством участников. "
                        +
                        "Клиент фильтрует по isActive для показа доступных комнат.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Список комнат"),
                        @ApiResponse(responseCode = "401", description = "Не авторизован")
        })
        @GetMapping
        public List<RoomResponse> getRooms() {
                return roomService.getAllRooms();
        }

        /**
         * GET /api/rooms/my
         * Returns all rooms the current user is a member of.
         */
        @Operation(summary = "Комнаты текущего пользователя", description = "Возвращает список комнат, в которых состоит текущий пользователь.")
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
         * Join a room. If the room is full, a duplicate is created automatically
         * and the user is added to the new room.
         */
        @Operation(summary = "Войти в комнату", description = "Присоединяет текущего пользователя к комнате. " +
                        "Если комната заполнена, автоматически создаётся новая комната-дубликат.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Успешно присоединился"),
                        @ApiResponse(responseCode = "400", description = "Пользователь уже в этой комнате"),
                        @ApiResponse(responseCode = "401", description = "Не авторизован"),
                        @ApiResponse(responseCode = "404", description = "Комната не найдена")
        })
        @PostMapping("/{roomId}/join")
        public void joinRoom(
                        @PathVariable UUID roomId,
                        @AuthenticationPrincipal User currentUser) {
                roomService.joinRoom(roomId, currentUser.getId());
        }

        /**
         * POST /api/rooms/{roomId}/leave
         * Leave a room. If the room was full and now has space, it becomes active
         * again.
         */
        @Operation(summary = "Покинуть комнату", description = "Удаляет текущего пользователя из комнаты. " +
                        "Если комната была закрыта (isActive=false) и после выхода появилось место — она снова становится активной.")
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
                        @AuthenticationPrincipal User currentUser) {
                roomService.leaveRoom(roomId, currentUser.getId());
        }
}
