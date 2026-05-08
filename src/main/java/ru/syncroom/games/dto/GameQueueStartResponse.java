package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameQueueStartResponse {
    private String gameSessionId;
}
