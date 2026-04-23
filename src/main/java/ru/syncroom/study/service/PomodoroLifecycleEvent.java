package ru.syncroom.study.service;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PomodoroLifecycleEvent {
    public enum Type {
        STARTED,
        PHASE_CHANGED,
        FINISHED
    }

    private final UUID roomId;
    private final Type type;
    private final String phase;
    private final Integer currentRound;
}
