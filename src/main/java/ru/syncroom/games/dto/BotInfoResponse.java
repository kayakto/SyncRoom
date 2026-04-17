package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BotInfoResponse {
    private String id;
    private String name;
    private String avatarUrl;
    private String botType;
}
