package ru.syncroom.points.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePointRequest {

    @NotBlank
    private String context; // "work", "study", "sport", "leisure"

    @NotBlank
    private String title;

    @NotBlank
    private String address;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;
}
