-- Parallel game queues per room (one row per game_type per room).
CREATE TABLE game_queues (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id               UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    game_type             VARCHAR(32) NOT NULL,
    status                VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    linked_game_session_id UUID,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    marked_empty_at       TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_game_queues_room_type UNIQUE (room_id, game_type)
);

CREATE INDEX idx_game_queues_room ON game_queues(room_id);
CREATE INDEX idx_game_queues_linked_session ON game_queues(linked_game_session_id);
CREATE INDEX idx_game_queues_marked_empty ON game_queues(marked_empty_at) WHERE marked_empty_at IS NOT NULL;

CREATE TABLE game_queue_players (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id   UUID NOT NULL REFERENCES game_queues(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_ready   BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_game_queue_players_queue_user UNIQUE (queue_id, user_id)
);

CREATE INDEX idx_game_queue_players_queue ON game_queue_players(queue_id);
CREATE INDEX idx_game_queue_players_user ON game_queue_players(user_id);

CREATE TABLE game_queue_bots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id    UUID NOT NULL REFERENCES game_queues(id) ON DELETE CASCADE,
    bot_user_id UUID NOT NULL REFERENCES bot_user(id) ON DELETE CASCADE,
    difficulty  VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',

    CONSTRAINT uq_game_queue_bots_queue_bot UNIQUE (queue_id, bot_user_id)
);

CREATE INDEX idx_game_queue_bots_queue ON game_queue_bots(queue_id);
