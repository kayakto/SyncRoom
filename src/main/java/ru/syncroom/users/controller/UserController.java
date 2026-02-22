package ru.syncroom.users.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.dto.ProfileResponse;
import ru.syncroom.users.dto.UpdateProfileRequest;
import ru.syncroom.users.service.UserService;

/**
 * REST controller for user profile operations.
 */
@Tag(name = "User Profile", description = "API для работы с профилем пользователя")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Получить текущий профиль",
            description = "Возвращает профиль текущего аутентифицированного пользователя."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль успешно получен",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getCurrentProfile(@AuthenticationPrincipal User user) {
        ProfileResponse profile = userService.getProfile(user.getId());
        return ResponseEntity.ok(profile);
    }

    @Operation(
            summary = "Обновить профиль",
            description = "Обновляет профиль текущего аутентифицированного пользователя. " +
                    "Email должен быть уникальным. Имя и email обязательны."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль успешно обновлен",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Неверные данные (email уже существует, валидация)"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        ProfileResponse updatedProfile = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(updatedProfile);
    }
}
