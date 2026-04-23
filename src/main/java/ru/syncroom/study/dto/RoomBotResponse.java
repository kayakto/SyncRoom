package ru.syncroom.study.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomBotResponse {
    private String botId;
    private String botType;
    private boolean isActive;
    private String config;
}
