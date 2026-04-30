package ru.syncroom.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.auth.dto.*;
import ru.syncroom.auth.service.AuthService;
import ru.syncroom.auth.web.AuthCookieService;
import ru.syncroom.common.config.AuthCookieProperties;

/**
 * REST controller for authentication endpoints.
 */
@Tag(name = "Authentication", description = "API для аутентификации пользователей")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final AuthCookieProperties authCookieProperties;

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
    public ResponseEntity<AuthResponse> oauth(
            @Valid @RequestBody OAuthRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.authenticateOAuth(request.getProvider(), request.getAccessToken(), request.getRedirectUri());
        authCookieService.writeAuthCookies(servletResponse, response.getAccessToken(), response.getRefreshToken());
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
    public ResponseEntity<AuthResponse> email(
            @Valid @RequestBody EmailAuthRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.authenticateEmail(request.getEmail(), request.getPassword());
        authCookieService.writeAuthCookies(servletResponse, response.getAccessToken(), response.getRefreshToken());
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
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.register(request.getEmail(), request.getPassword(), request.getName());
        authCookieService.writeAuthCookies(servletResponse, response.getAccessToken(), response.getRefreshToken());
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
    public ResponseEntity<RefreshResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String refreshToken = (request != null ? request.getRefreshToken() : null);
        if (refreshToken == null || refreshToken.isBlank()) {
            var cookies = servletRequest.getCookies();
            if (cookies != null) {
                for (var cookie : cookies) {
                    if (authCookieProperties.getRefreshTokenName().equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }
        }
        RefreshResponse response = authService.refresh(refreshToken);
        authCookieService.writeAuthCookies(servletResponse, response.getAccessToken(), response.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Выход из системы", description = "Очищает auth cookies для web-клиента")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse servletResponse) {
        authCookieService.clearAuthCookies(servletResponse);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Получить CSRF токен", description = "Инициализирует и возвращает CSRF token для web-клиента")
    @GetMapping("/csrf")
    public ResponseEntity<String> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken.getToken());
    }
}
