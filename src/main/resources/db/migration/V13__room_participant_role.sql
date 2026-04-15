ALTER TABLE room_participants
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'OBSERVER';

-- Синхронизация с уже занятыми местами (если были данные до деплоя)
UPDATE room_participants rp
SET role = 'PARTICIPANT'
FROM seats s
WHERE s.room_id = rp.room_id
  AND s.occupied_by = rp.user_id;
