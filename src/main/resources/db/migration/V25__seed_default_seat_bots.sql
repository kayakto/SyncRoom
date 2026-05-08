-- Default seat bots for existing rooms (idempotent). Mirrors DefaultSeatBotsByContext + seat index (ORDER BY x, y).

-- STUDY: first seat → STUDY_HELPER, second → WORK_FOCUS_BUDDY
INSERT INTO room_seat_bots (id, room_id, bot_type, seat_id, name, avatar_url, created_at)
SELECT gen_random_uuid(), ar.room_id, 'STUDY_HELPER', ar.seat_id, 'Учебник Андрюша', NULL, NOW()
FROM (
    SELECT r.id AS room_id, s.id AS seat_id,
           ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY s.x, s.y) AS rn
    FROM rooms r
    JOIN seats s ON s.room_id = r.id
    WHERE LOWER(TRIM(r.context)) = 'study'
) ar
WHERE ar.rn = 1
  AND NOT EXISTS (SELECT 1 FROM seats st WHERE st.id = ar.seat_id AND st.occupied_by IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.seat_id = ar.seat_id)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.room_id = ar.room_id AND b.bot_type = 'STUDY_HELPER');

INSERT INTO room_seat_bots (id, room_id, bot_type, seat_id, name, avatar_url, created_at)
SELECT gen_random_uuid(), ar.room_id, 'WORK_FOCUS_BUDDY', ar.seat_id, 'Lofi Cat', NULL, NOW()
FROM (
    SELECT r.id AS room_id, s.id AS seat_id,
           ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY s.x, s.y) AS rn
    FROM rooms r
    JOIN seats s ON s.room_id = r.id
    WHERE LOWER(TRIM(r.context)) = 'study'
) ar
WHERE ar.rn = 2
  AND NOT EXISTS (SELECT 1 FROM seats st WHERE st.id = ar.seat_id AND st.occupied_by IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.seat_id = ar.seat_id)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.room_id = ar.room_id AND b.bot_type = 'WORK_FOCUS_BUDDY');

-- WORK: first seat → WORK_FOCUS_BUDDY (only if this room is not study — study already has WORK on seat 2)
INSERT INTO room_seat_bots (id, room_id, bot_type, seat_id, name, avatar_url, created_at)
SELECT gen_random_uuid(), ar.room_id, 'WORK_FOCUS_BUDDY', ar.seat_id, 'Lofi Cat', NULL, NOW()
FROM (
    SELECT r.id AS room_id, s.id AS seat_id,
           ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY s.x, s.y) AS rn
    FROM rooms r
    JOIN seats s ON s.room_id = r.id
    WHERE LOWER(TRIM(r.context)) = 'work'
) ar
WHERE ar.rn = 1
  AND NOT EXISTS (SELECT 1 FROM seats st WHERE st.id = ar.seat_id AND st.occupied_by IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.seat_id = ar.seat_id)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.room_id = ar.room_id AND b.bot_type = 'WORK_FOCUS_BUDDY');

-- SPORT: first seat → SPORT_CHEERLEADER
INSERT INTO room_seat_bots (id, room_id, bot_type, seat_id, name, avatar_url, created_at)
SELECT gen_random_uuid(), ar.room_id, 'SPORT_CHEERLEADER', ar.seat_id, 'Тренер Витя', NULL, NOW()
FROM (
    SELECT r.id AS room_id, s.id AS seat_id,
           ROW_NUMBER() OVER (PARTITION BY r.id ORDER BY s.x, s.y) AS rn
    FROM rooms r
    JOIN seats s ON s.room_id = r.id
    WHERE LOWER(TRIM(r.context)) = 'sport'
) ar
WHERE ar.rn = 1
  AND NOT EXISTS (SELECT 1 FROM seats st WHERE st.id = ar.seat_id AND st.occupied_by IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.seat_id = ar.seat_id)
  AND NOT EXISTS (SELECT 1 FROM room_seat_bots b WHERE b.room_id = ar.room_id AND b.bot_type = 'SPORT_CHEERLEADER');

-- Participant rows for bots that have no participant yet (e.g. rows inserted only via SQL)
INSERT INTO room_participants (id, room_id, user_id, seat_bot_id, joined_at, role)
SELECT gen_random_uuid(), b.room_id, NULL, b.id, NOW(), 'PARTICIPANT'
FROM room_seat_bots b
WHERE NOT EXISTS (SELECT 1 FROM room_participants p WHERE p.seat_bot_id = b.id);
