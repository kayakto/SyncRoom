package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for token refresh.
 */
@Schema(description = "Запрос на обновление токенов")
@Data
public class RefreshRequest {
    
    @Schema(description = "Refresh токен для обновления пары токенов", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
