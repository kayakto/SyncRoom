package ru.syncroom.games.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddBotRequest {
    @NotBlank
    private String botType;

    @Min(1)
    @Max(5)
    private Integer count = 1;
}
