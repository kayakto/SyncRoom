package ru.syncroom.projector.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectorReportResponse {
    String queueItemId;
    int reportsCount;
    int threshold;
    boolean removed;
}
