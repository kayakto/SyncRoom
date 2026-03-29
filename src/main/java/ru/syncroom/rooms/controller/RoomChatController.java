package ru.syncroom.rooms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.rooms.dto.PagedChatMessagesResponse;
import ru.syncroom.rooms.service.RoomChatService;
import ru.syncroom.users.domain.User;

import java.util.UUID;

@Tag(name = "Room chat", description = "Чат комнаты (история сообщений)")
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class RoomChatController {

    private final RoomChatService roomChatService;

    @Operation(summary = "История сообщений чата комнаты (постранично)")
    @GetMapping
    public PagedChatMessagesResponse getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return roomChatService.getMessages(roomId, currentUser.getId(), page, size);
    }
}
