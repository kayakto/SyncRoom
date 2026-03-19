ALTER TABLE pomodoro_sessions
    ADD COLUMN paused_phase VARCHAR(16),
    ADD COLUMN remaining_seconds INT;

