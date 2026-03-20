package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GamePlayerDto {
    private String id;
    private String name;
    private String avatarUrl;
    private Boolean isReady;
    private Integer score;
}

