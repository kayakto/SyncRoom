CREATE TABLE projector_queue_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id                UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode                   VARCHAR(16) NOT NULL,
    video_url              TEXT,
    video_title            VARCHAR(500),
    requested_duration_sec INT,
    slot_duration_sec      INT NOT NULL,
    status                 VARCHAR(16) NOT NULL, -- WAITING, PLAYING, DONE
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at             TIMESTAMP WITH TIME ZONE,
    finished_at            TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_projector_queue_room_created ON projector_queue_items(room_id, created_at);
CREATE INDEX idx_projector_queue_room_status ON projector_queue_items(room_id, status);
