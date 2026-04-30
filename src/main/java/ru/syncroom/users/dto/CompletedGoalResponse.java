package ru.syncroom.users.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.UUID;

@Value
@Builder
public class CompletedGoalResponse {
    UUID id;
    String text;
    UUID roomId;
    String roomTitle;
    OffsetDateTime completedAt;
}
