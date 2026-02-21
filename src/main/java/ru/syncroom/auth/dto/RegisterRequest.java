package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for user registration.
 */
@Schema(description = "Запрос на регистрацию нового пользователя")
@Data
public class RegisterRequest {
    
    @Schema(description = "Email пользователя (должен быть уникальным)", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @Schema(description = "Пароль (минимум 6 символов)", example = "password123", minLength = 6)
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    @Schema(description = "Имя пользователя", example = "John Doe")
    @NotBlank(message = "Name is required")
    private String name;
}
