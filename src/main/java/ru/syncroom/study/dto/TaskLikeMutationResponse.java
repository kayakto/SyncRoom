package ru.syncroom.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskLikeMutationResponse {
    private String taskId;
    private long likeCount;
    @JsonProperty("likedByMe")
    private boolean likedByMe;
}
