package ru.syncroom.rooms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.rooms.dto.SeatBotCatalogEntryDto;
import ru.syncroom.rooms.dto.SeatBotInRoomDto;
import ru.syncroom.rooms.service.SeatBotService;
import ru.syncroom.users.domain.User;

import java.util.List;
import java.util.UUID;

@Tag(name = "Seat bots", description = "Боты на местах (work/study/sport)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class SeatBotController {

    private final SeatBotService seatBotService;

    @GetMapping("/api/rooms/seat-bots/catalog")
    @Operation(summary = "Каталог seat-ботов для контекста комнаты")
    public List<SeatBotCatalogEntryDto> catalog(
            @RequestParam("context") String context,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatBotService.catalog(context, currentUser.getId());
    }

    @GetMapping("/api/rooms/{roomId}/seat-bots")
    @Operation(summary = "Список seat-ботов в комнате")
    public List<SeatBotInRoomDto> listInRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser
    ) {
        return seatBotService.listInRoom(roomId, currentUser.getId());
    }
}
