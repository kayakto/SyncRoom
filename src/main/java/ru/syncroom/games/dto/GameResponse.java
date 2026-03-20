package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GameResponse {
    private String gameId;
    private String gameType;
    private String status;
    private List<GamePlayerDto> players;
}

