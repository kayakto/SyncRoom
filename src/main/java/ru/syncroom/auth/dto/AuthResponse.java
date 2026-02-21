package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for authentication operations.
 */
@Schema(description = "Ответ на запрос аутентификации")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    @Schema(description = "JWT access токен (действителен 15 минут)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;
    
    @Schema(description = "JWT refresh токен (действителен 30 дней)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
    
    @Schema(description = "Флаг первого входа (true для новых пользователей)", example = "true")
    private Boolean isFirstLogin;
    
    @Schema(description = "Информация о пользователе")
    private UserDto user;
    
    @Schema(description = "Информация о пользователе")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
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
}
