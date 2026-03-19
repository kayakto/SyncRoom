package ru.syncroom.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponse {
    private String id;
    private String text;
    @JsonProperty("isDone")
    private boolean isDone;
    private int sortOrder;
}

