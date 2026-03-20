package ru.syncroom.games.dto;

import lombok.Data;

import java.util.Map;

@Data
public class GameActionMessage {
    private String type;
    private Map<String, Object> payload;
}

