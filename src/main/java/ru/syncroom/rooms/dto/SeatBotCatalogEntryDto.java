package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SeatBotCatalogEntryDto {
    String botType;
    String name;
    String avatarUrl;
    String description;
    List<String> supportedContexts;
    BehaviourDto behaviour;

    @Value
    @Builder
    public static class BehaviourDto {
        boolean reactsToPomodoro;
        boolean createsGoals;
        boolean likesGoals;
    }
}
