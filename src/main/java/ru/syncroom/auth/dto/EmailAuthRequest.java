package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for email/password authentication.
 */
@Schema(description = "Запрос на аутентификацию по email и паролю")
@Data
public class EmailAuthRequest {
    
    @Schema(description = "Email пользователя", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @Schema(description = "Пароль пользователя", example = "password123")
    @NotBlank(message = "Password is required")
    private String password;
}
