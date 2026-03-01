CREATE TABLE rooms (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context           VARCHAR(32) NOT NULL,       -- 'work', 'study', 'sport', 'leisure'
    title             VARCHAR(255) NOT NULL,      -- Название комнаты
    max_participants  INT NOT NULL DEFAULT 10,    -- Максимум участников
    is_active         BOOLEAN NOT NULL DEFAULT TRUE, -- Доступна ли для входа
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rooms_context ON rooms(context);
CREATE INDEX idx_rooms_active ON rooms(is_active);

CREATE TABLE room_participants (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id   UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE(room_id, user_id)
);

CREATE INDEX idx_room_participants_room ON room_participants(room_id);
CREATE INDEX idx_room_participants_user ON room_participants(user_id);

-- Начальные комнаты (по одной на каждый контекст)
INSERT INTO rooms (context, title, max_participants) VALUES
    ('leisure', 'Дом',                    10),
    ('work',    'Работа',                 10),
    ('study',   'Школа / Университет',    10),
    ('sport',   'Спортзал / Спорт',       10);
