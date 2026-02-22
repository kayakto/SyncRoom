package ru.syncroom.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for user profile.
 */
@Schema(description = "Профиль пользователя")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    
    @Schema(description = "Уникальный идентификатор пользователя", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    
    @Schema(description = "Имя пользователя", example = "John Doe")
    private String name;
    
    @Schema(description = "Email пользователя", example = "user@example.com")
    private String email;
    
    @Schema(description = "Провайдер аутентификации", example = "email", allowableValues = {"vk", "yandex", "email"})
    private String provider;
    
    @Schema(description = "URL аватара пользователя", example = "https://example.com/avatar.jpg", nullable = true)
    private String avatarUrl;
}
