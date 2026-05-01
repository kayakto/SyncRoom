package ru.syncroom.rooms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ru.syncroom.rooms.dto.SeatBotPlaceRequest;
import ru.syncroom.rooms.dto.SeatDto;
import ru.syncroom.rooms.service.SeatBotService;
import ru.syncroom.rooms.service.SeatService;
import ru.syncroom.users.domain.User;

import java.util.UUID;

/**
 * REST controller for seat mechanics.
 *
 * WebSocket:
 *   Subscribe: /topic/room/{roomId}/seats
 *   Events:    SEAT_TAKEN, SEAT_LEFT
 */
@Tag(name = "Seats", description = "API для работы с местами в комнате")
@RestController
@RequestMapping("/api/rooms/{roomId}/seats")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class SeatController {

    private final SeatService seatService;
    private final SeatBotService seatBotService;

    /**
     * POST /api/rooms/{roomId}/seats/{seatId}/sit
     *
     * Occupy a seat. If the user is already on another seat in this room,
     * they are automatically moved (old seat → SEAT_LEFT, new seat → SEAT_TAKEN).
     */
    @Operation(
            summary = "Сесть на место",
            description = "Занимает место. Если пользователь уже сидит в этой комнате — автоматически пересаживает. " +
                    "Рассылает WebSocket-событие SEAT_TAKEN на /topic/room/{roomId}/seats."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место занято, возвращает обновлённый SeatDto"),
            @ApiResponse(responseCode = "404", description = "Комната или место не найдено"),
            @ApiResponse(responseCode = "409", description = "Место уже занято другим пользователем"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @PostMapping("/{seatId}/sit")
    public SeatDto sit(
            @PathVariable UUID roomId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatService.sit(roomId, seatId, currentUser.getId());
    }

    /**
     * POST /api/rooms/{roomId}/seats/{seatId}/leave
     *
     * Stand up from a seat. Only the occupier can stand up.
     */
    @Operation(
            summary = "Встать с места",
            description = "Освобождает место. Только тот, кто сидит, может встать. " +
                    "Рассылает WebSocket-событие SEAT_LEFT на /topic/room/{roomId}/seats."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место освобождено, occupiedBy = null"),
            @ApiResponse(responseCode = "403", description = "Это не ваше место"),
            @ApiResponse(responseCode = "404", description = "Место не найдено"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @PostMapping("/{seatId}/leave")
    public SeatDto leave(
            @PathVariable UUID roomId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatService.standUp(roomId, seatId, currentUser.getId());
    }

    @Operation(summary = "Посадить seat-бота на место")
    @PostMapping("/{seatId}/bot")
    public SeatDto placeBot(
            @PathVariable UUID roomId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody SeatBotPlaceRequest body
    ) {
        return seatBotService.placeBot(roomId, seatId, body.getBotType(), currentUser.getId());
    }

    @Operation(summary = "Снять seat-бота с места")
    @DeleteMapping("/{seatId}/bot")
    public SeatDto removeBot(
            @PathVariable UUID roomId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatBotService.removeBotFromSeat(roomId, seatId, currentUser.getId());
    }

    @Operation(summary = "Снять seat-бота с места (алиас)")
    @PostMapping("/{seatId}/bot/leave")
    public SeatDto removeBotPost(
            @PathVariable UUID roomId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatBotService.removeBotFromSeat(roomId, seatId, currentUser.getId());
    }
}
