package ru.syncroom.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.auth.dto.*;
import ru.syncroom.auth.service.AuthService;

/**
 * REST controller for authentication endpoints.
 */
@Tag(name = "Authentication", description = "API для аутентификации пользователей")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "OAuth аутентификация",
            description = "Аутентификация через внешний OAuth провайдер (VK или Yandex). " +
                    "Если пользователь не существует, он будет автоматически создан."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная аутентификация",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (неверный провайдер)"),
            @ApiResponse(responseCode = "401", description = "Ошибка аутентификации")
    })
    @PostMapping("/oauth")
    public ResponseEntity<AuthResponse> oauth(@Valid @RequestBody OAuthRequest request) {
        AuthResponse response = authService.authenticateOAuth(request.getProvider(), request.getAccessToken());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Аутентификация по email и паролю",
            description = "Вход в систему используя email и пароль. Пользователь должен быть зарегистрирован."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная аутентификация",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неверный email или пароль")
    })
    @PostMapping("/email")
    public ResponseEntity<AuthResponse> email(@Valid @RequestBody EmailAuthRequest request) {
        AuthResponse response = authService.authenticateEmail(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Регистрация нового пользователя",
            description = "Регистрирует нового пользователя с email и паролем. " +
                    "Email должен быть уникальным. Пароль должен содержать минимум 6 символов."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная регистрация",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email уже существует или неверные данные")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request.getEmail(), request.getPassword(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Обновление токенов",
            description = "Обновляет access и refresh токены используя действующий refresh токен. " +
                    "Возвращает новую пару токенов."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токены успешно обновлены",
                    content = @Content(schema = @Schema(implementation = RefreshResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неверный или истёкший refresh токен")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
