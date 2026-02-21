package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for token refresh operation.
 */
@Schema(description = "Ответ на запрос обновления токенов")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {
    
    @Schema(description = "Новый JWT access токен (действителен 15 минут)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;
    
    @Schema(description = "Новый JWT refresh токен (действителен 30 дней)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}
