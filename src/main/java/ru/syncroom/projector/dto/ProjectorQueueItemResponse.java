package ru.syncroom.projector.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectorQueueItemResponse {
    String queueItemId;
    String userId;
    String userName;
    String mode;
    String videoUrl;
    String videoTitle;
    int slotDurationSec;
    String status;
}
