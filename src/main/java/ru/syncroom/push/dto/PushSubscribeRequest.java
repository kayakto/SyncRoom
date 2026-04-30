package ru.syncroom.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PushSubscribeRequest {

    @NotBlank(message = "endpoint is required")
    private String endpoint;

    @Valid
    @NotNull(message = "keys is required")
    private Keys keys;

    @Data
    public static class Keys {
        @NotBlank(message = "keys.p256dh is required")
        private String p256dh;

        @NotBlank(message = "keys.auth is required")
        private String auth;
    }
}
