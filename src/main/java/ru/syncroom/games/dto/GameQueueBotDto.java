package ru.syncroom.games.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameQueueBotDto {
    private String botId;
    private String name;
    private String avatarUrl;
    private String difficulty;
    @Builder.Default
    @JsonProperty("isBot")
    private boolean isBot = true;
    @Builder.Default
    @JsonProperty("isReady")
    private boolean isReady = true;
}
