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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.dto.ProfileResponse;
import ru.syncroom.users.dto.UpdateProfileRequest;
import ru.syncroom.users.dto.UserStatsResponse;
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

    @Operation(
            summary = "Загрузить аватар",
            description = "Multipart поле `file` — PNG, JPEG или WebP (до 2 MB). В профиль сохраняется публичный URL (local: API /api/media/avatars/{userId}, s3: CDN).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UploadAvatarRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аватар успешно загружен",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Неверный файл"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileResponse> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file
    ) throws java.io.IOException {
        return ResponseEntity.ok(userService.uploadAvatar(user.getId(), file));
    }

    @Schema(name = "UploadAvatarRequest", description = "Multipart-запрос загрузки аватара")
    private static class UploadAvatarRequest {
        @Schema(description = "Файл изображения (PNG/JPEG/WebP)", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        public MultipartFile file;
    }

    @Operation(
            summary = "Получить статистику текущего пользователя",
            description = "Возвращает агрегированную статистику по выполненным целям и лайкам, а также недавнюю историю выполненных целей."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статистика успешно получена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsResponse> getCurrentUserStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getStats(user.getId()));
    }
}
