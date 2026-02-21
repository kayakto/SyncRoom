package ru.syncroom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for OAuth authentication.
 */
@Schema(description = "Запрос на OAuth аутентификацию")
@Data
public class OAuthRequest {
    
    @Schema(description = "OAuth провайдер", example = "vk", allowableValues = {"vk", "yandex"})
    @NotBlank(message = "Provider is required")
    private String provider; // "vk" or "yandex"
    
    @Schema(description = "Access token от внешнего провайдера", example = "vk_access_token_12345")
    @NotBlank(message = "Access token is required")
    private String accessToken;
}
