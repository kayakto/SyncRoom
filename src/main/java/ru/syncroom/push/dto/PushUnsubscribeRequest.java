package ru.syncroom.push.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushUnsubscribeRequest {

    @NotBlank(message = "endpoint is required")
    private String endpoint;
}
