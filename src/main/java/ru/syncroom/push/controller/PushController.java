package ru.syncroom.push.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.syncroom.push.dto.PushSubscribeRequest;
import ru.syncroom.push.dto.PushSubscriptionResponse;
import ru.syncroom.push.dto.PushUnsubscribeRequest;
import ru.syncroom.push.service.PushService;
import ru.syncroom.users.domain.User;

@Tag(name = "Push", description = "API для web push подписок (VAPID)")
@RestController
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @GetMapping(value = "/api/push/vapid-public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Получить публичный VAPID ключ")
    public String getVapidPublicKey() {
        return pushService.getVapidPublicKey();
    }

    @PostMapping("/api/push/subscribe")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Сохранить/обновить push подписку текущего пользователя")
    public PushSubscriptionResponse subscribe(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PushSubscribeRequest request
    ) {
        return pushService.subscribe(currentUser.getId(), request);
    }

    @PostMapping("/api/push/unsubscribe")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Удалить push подписку текущего пользователя")
    public PushSubscriptionResponse unsubscribe(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody PushUnsubscribeRequest request
    ) {
        return pushService.unsubscribe(currentUser.getId(), request);
    }
}
