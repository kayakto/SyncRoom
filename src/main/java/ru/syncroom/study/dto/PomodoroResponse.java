package ru.syncroom.study.dto;

import lombok.Builder;
import lombok.Data;
import ru.syncroom.projector.dto.UserDto;

@Data
@Builder
public class PomodoroResponse {
    private String id;
    private String roomId;
    private UserDto startedBy;
    private String phase;           // WORK, BREAK, LONG_BREAK, PAUSED, FINISHED
    private int currentRound;
    private int roundsTotal;
    private String phaseEndAt;      // ISO 8601, null если PAUSED
    private int workDuration;       // секунды
    private int breakDuration;
    private int longBreakDuration;
}

