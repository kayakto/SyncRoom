package ru.syncroom.rooms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SeatBotPlaceRequest {
    @NotBlank
    private String botType;
}
