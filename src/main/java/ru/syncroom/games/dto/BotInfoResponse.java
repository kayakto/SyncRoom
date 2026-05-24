package ru.syncroom.games.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BotInfoResponse {
    private String id;
    private String name;
    private String avatarUrl;
    private String botType;
    /** Игры, для которых подходит бот: {@code QUIPLASH} или {@code GARTIC_PHONE}. */
    private List<String> supportedGameTypes;
}
