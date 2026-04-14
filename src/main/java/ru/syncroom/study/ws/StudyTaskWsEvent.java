package ru.syncroom.study.ws;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class StudyTaskWsEvent {

    private StudyTaskWsEventType type;
    private Object payload;
    private OffsetDateTime timestamp;

    public static StudyTaskWsEvent of(StudyTaskWsEventType type, Object payload) {
        return StudyTaskWsEvent.builder()
                .type(type)
                .payload(payload)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
