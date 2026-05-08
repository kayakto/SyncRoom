-- Persist pomodoro auto-restart schedule across backend restarts (FINISHED + next_restart_at).
ALTER TABLE pomodoro_sessions ADD COLUMN next_restart_at TIMESTAMP WITH TIME ZONE NULL;
