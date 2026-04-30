package ru.syncroom.users.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class UserStatsResponse {
    long completedToday;
    long completedThisWeek;
    long completedTotal;
    long totalLikesReceived;
    List<CompletedGoalResponse> recentCompletedGoals;
}
