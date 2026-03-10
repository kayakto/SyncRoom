CREATE TABLE seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID    NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    x           FLOAT   NOT NULL CHECK (x >= 0.0 AND x <= 1.0),
    y           FLOAT   NOT NULL CHECK (y >= 0.0 AND y <= 1.0),
    occupied_by UUID    REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_seats_room     ON seats(room_id);
CREATE INDEX idx_seats_occupied ON seats(occupied_by);

-- Seed: 10 seats per initial room, laid out in a 2-row grid (normalised coords)
-- Row 1: y=0.30, Row 2: y=0.65,  x positions: 0.1, 0.25, 0.4, 0.55, 0.7
INSERT INTO seats (room_id, x, y)
SELECT r.id, coords.x, coords.y
FROM rooms r
CROSS JOIN (VALUES
    (0.10, 0.30), (0.25, 0.30), (0.40, 0.30), (0.55, 0.30), (0.70, 0.30),
    (0.10, 0.65), (0.25, 0.65), (0.40, 0.65), (0.55, 0.65), (0.70, 0.65)
) AS coords(x, y);
