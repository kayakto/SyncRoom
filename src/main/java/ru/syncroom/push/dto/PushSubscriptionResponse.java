package ru.syncroom.push.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PushSubscriptionResponse {
    String endpoint;
    boolean subscribed;
}
