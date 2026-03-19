package ru.syncroom.study.dto;

import lombok.Data;

@Data
public class UpdateTaskRequest {
    private String text;        // null = не менять
    private Boolean isDone;     // null = не менять
    private Integer sortOrder;  // null = не менять
}

