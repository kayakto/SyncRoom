package ru.syncroom.study.dto;

import lombok.Data;

@Data
public class PomodoroManagerConfigRequest {
    private Boolean autoStart;
    private Integer autoStartDelay;
    private Boolean autoRestart;
    private Integer restartDelay;
    private Integer workDuration;
    private Integer breakDuration;
    private Integer longBreakDuration;
    private Integer roundsTotal;
    private Boolean sendReminders;
    private Boolean sendMotivation;
}
