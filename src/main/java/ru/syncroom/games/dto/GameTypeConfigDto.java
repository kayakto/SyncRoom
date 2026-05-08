package ru.syncroom.games.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameTypeConfigDto {
    private int minPlayers;
    private int maxPlayers;
    private int estimatedDurationMin;
}
