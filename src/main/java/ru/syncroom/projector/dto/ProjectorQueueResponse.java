package ru.syncroom.projector.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProjectorQueueResponse {
    List<ProjectorQueueItemResponse> items;
}
