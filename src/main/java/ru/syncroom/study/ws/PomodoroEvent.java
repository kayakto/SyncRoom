package ru.syncroom.study.ws;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PomodoroEvent {

    private PomodoroEventType type;
    private Object payload;
    private OffsetDateTime timestamp;

    public static PomodoroEvent of(PomodoroEventType type, Object payload) {
        return PomodoroEvent.builder()
                .type(type)
                .payload(payload)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}

