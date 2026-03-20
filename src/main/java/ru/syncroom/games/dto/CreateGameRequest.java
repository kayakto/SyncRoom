package ru.syncroom.games.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateGameRequest {
    @NotBlank
    private String gameType; // QUIPLASH, GARTIC_PHONE
}

