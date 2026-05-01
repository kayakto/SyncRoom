-- Seat bots: visible participants on seats (work/study/sport; not leisure in MVP)

CREATE TABLE room_seat_bots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    bot_type    VARCHAR(50) NOT NULL,
    seat_id     UUID NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    avatar_url  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (room_id, bot_type),
    UNIQUE (seat_id)
);

CREATE INDEX idx_room_seat_bots_room ON room_seat_bots(room_id);

-- Занятость места ботом: строка в room_seat_bots с seat_id (без второго FK на seats).

ALTER TABLE room_participants
    ADD COLUMN seat_bot_id UUID REFERENCES room_seat_bots(id) ON DELETE CASCADE;

ALTER TABLE room_participants
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE room_participants
    ADD CONSTRAINT chk_room_participant_user_or_seat_bot
        CHECK (
            (user_id IS NOT NULL AND seat_bot_id IS NULL)
                OR (user_id IS NULL AND seat_bot_id IS NOT NULL)
            );

ALTER TABLE room_participants
    DROP CONSTRAINT IF EXISTS room_participants_room_id_user_id_key;

CREATE UNIQUE INDEX uq_room_participants_room_user
    ON room_participants (room_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_room_participants_room_seat_bot
    ON room_participants (room_id, seat_bot_id)
    WHERE seat_bot_id IS NOT NULL;

-- Tasks may belong to a seat bot instead of a user
ALTER TABLE study_tasks
    ADD COLUMN owner_seat_bot_id UUID REFERENCES room_seat_bots(id) ON DELETE CASCADE;

ALTER TABLE study_tasks
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE study_tasks
    ADD CONSTRAINT chk_study_task_owner
        CHECK (
            (user_id IS NOT NULL AND owner_seat_bot_id IS NULL)
                OR (user_id IS NULL AND owner_seat_bot_id IS NOT NULL)
            );

CREATE INDEX idx_study_tasks_owner_seat_bot ON study_tasks(owner_seat_bot_id);

-- Likes may come from a seat bot (counts same as human likes for leaderboard)
ALTER TABLE task_like
    ADD COLUMN liker_seat_bot_id UUID REFERENCES room_seat_bots(id) ON DELETE CASCADE;

ALTER TABLE task_like
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE task_like
    DROP CONSTRAINT IF EXISTS task_like_task_id_user_id_key;

ALTER TABLE task_like
    ADD CONSTRAINT chk_task_like_liker
        CHECK (
            (user_id IS NOT NULL AND liker_seat_bot_id IS NULL)
                OR (user_id IS NULL AND liker_seat_bot_id IS NOT NULL)
            );

CREATE UNIQUE INDEX uq_task_like_task_user
    ON task_like (task_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_task_like_task_seat_bot
    ON task_like (task_id, liker_seat_bot_id)
    WHERE liker_seat_bot_id IS NOT NULL;

CREATE INDEX idx_task_like_liker_seat_bot ON task_like(liker_seat_bot_id);
