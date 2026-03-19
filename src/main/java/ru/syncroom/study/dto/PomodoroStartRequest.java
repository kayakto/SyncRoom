package ru.syncroom.study.dto;

import lombok.Data;

@Data
public class PomodoroStartRequest {
    private Integer workDuration;       // null = 1500
    private Integer breakDuration;      // null = 300
    private Integer longBreakDuration;  // null = 900
    private Integer roundsTotal;        // null = 4
}

