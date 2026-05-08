package ru.syncroom.games.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GameQueueAddBotRequest {
    @NotBlank
    private String botId;
    @NotBlank
    private String difficulty;
}
