CREATE TABLE points (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context     VARCHAR(32) NOT NULL,       -- 'work', 'study', 'sport', 'leisure'
    title       VARCHAR(255) NOT NULL,      -- 'Дом', 'Работа', 'Школа / Университет', 'Спортзал / Спорт'
    address     TEXT NOT NULL,              -- Полный адрес (обратное геокодирование)
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_points_user_id ON points(user_id);
CREATE INDEX idx_points_user_context ON points(user_id, context);
