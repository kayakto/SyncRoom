-- Projector sessions: one active session per room.
-- mode = 'EMBED' (shared video link) or 'STREAM' (live RTMP → HLS).
CREATE TABLE projector_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    host_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode        VARCHAR(16) NOT NULL,          -- 'EMBED' or 'STREAM'
    video_url   TEXT,                          -- video URL (EMBED) or HLS URL (STREAM, auto-generated)
    video_title VARCHAR(500),
    stream_key  VARCHAR(255),                  -- stream key (STREAM only), e.g. 'room-{roomId}'
    is_playing  BOOLEAN NOT NULL DEFAULT FALSE,
    position_ms BIGINT  NOT NULL DEFAULT 0,    -- playback position in ms (EMBED only)
    is_live     BOOLEAN NOT NULL DEFAULT FALSE, -- stream is active (STREAM only, updated by SRS callback)
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE(room_id)  -- exactly one projector per room
);

CREATE INDEX idx_projector_room       ON projector_sessions(room_id);
CREATE INDEX idx_projector_stream_key ON projector_sessions(stream_key);
