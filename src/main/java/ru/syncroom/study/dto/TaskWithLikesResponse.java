package ru.syncroom.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskWithLikesResponse {
    private String id;
    private String text;
    @JsonProperty("isDone")
    private boolean isDone;
    private int sortOrder;
    private String ownerId;
    private String ownerName;
    @JsonProperty("isBot")
    private boolean isBot;
    private long likeCount;
    @JsonProperty("likedByMe")
    private boolean likedByMe;
}
