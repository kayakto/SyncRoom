package ru.syncroom.study.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaderboardEntryResponse {
    private String userId;
    private String userName;
    private String avatarUrl;
    private long totalLikes;
    private long completedTasks;
    private long totalTasks;
}
