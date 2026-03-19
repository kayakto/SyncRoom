-- Pomodoro sessions: one active timer per study room.
CREATE TABLE pomodoro_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    started_by          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phase               VARCHAR(16) NOT NULL DEFAULT 'WORK',   -- WORK, BREAK, LONG_BREAK, PAUSED, FINISHED
    work_duration       INT NOT NULL DEFAULT 1500,             -- seconds
    break_duration      INT NOT NULL DEFAULT 300,              -- seconds
    long_break_duration INT NOT NULL DEFAULT 900,              -- seconds
    rounds_total        INT NOT NULL DEFAULT 4,
    current_round       INT NOT NULL DEFAULT 1,
    phase_end_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE (room_id)
);

CREATE INDEX idx_pomodoro_room ON pomodoro_sessions(room_id);

-- Study tasks: personal tasks per user per room.
CREATE TABLE study_tasks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    text        TEXT NOT NULL,
    is_done     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_study_tasks_user_room ON study_tasks(user_id, room_id);

