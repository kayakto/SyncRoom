package ru.syncroom.games.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameQueuePlayerDto {
    private String userId;
    private String username;
    private String avatarUrl;
    @Builder.Default
    @JsonProperty("isBot")
    private boolean isBot = false;
    @JsonProperty("isReady")
    private boolean isReady;
}
