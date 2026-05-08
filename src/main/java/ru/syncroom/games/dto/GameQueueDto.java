package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GameQueueDto {
    private String gameType;
    private String status;
    private int minPlayers;
    private int maxPlayers;
    private List<GameQueuePlayerDto> players;
    private List<GameQueueBotDto> bots;
}
