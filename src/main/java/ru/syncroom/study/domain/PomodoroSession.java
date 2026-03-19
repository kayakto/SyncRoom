package ru.syncroom.study.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pomodoro_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PomodoroSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by", nullable = false)
    private User startedBy;

    @Column(nullable = false, length = 16)
    private String phase;  // WORK, BREAK, LONG_BREAK, PAUSED, FINISHED

    @Column(name = "work_duration", nullable = false)
    private Integer workDuration;

    @Column(name = "break_duration", nullable = false)
    private Integer breakDuration;

    @Column(name = "long_break_duration", nullable = false)
    private Integer longBreakDuration;

    @Column(name = "rounds_total", nullable = false)
    private Integer roundsTotal;

    @Column(name = "current_round", nullable = false)
    private Integer currentRound;

    @Column(name = "phase_end_at")
    private OffsetDateTime phaseEndAt;

    @Column(name = "paused_phase", length = 16)
    private String pausedPhase;

    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}

