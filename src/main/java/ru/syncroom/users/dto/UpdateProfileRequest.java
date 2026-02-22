package ru.syncroom.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for updating user profile.
 */
@Schema(description = "Запрос на обновление профиля пользователя")
@Data
public class UpdateProfileRequest {
    
    @Schema(description = "Имя пользователя", example = "John Doe")
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;
    
    @Schema(description = "Email пользователя (должен быть уникальным)", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @Schema(description = "URL аватара пользователя", example = "https://example.com/avatar.jpg", nullable = true)
    private String avatarUrl;
}
