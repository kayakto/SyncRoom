package ru.syncroom.projector.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectorEnqueueResponse {
    String queueItemId;
    String status; // PLAYING or QUEUED
    int position;
    int slotDurationSec;
    ProjectorResponse projector;
}
