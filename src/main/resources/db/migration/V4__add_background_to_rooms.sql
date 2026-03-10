-- Add background image URL to rooms
ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS background_picture TEXT;

-- Seed background pictures for initial rooms
UPDATE rooms SET background_picture = 'https://storage.syncroom.app/backgrounds/leisure.jpg' WHERE context = 'leisure';
UPDATE rooms SET background_picture = 'https://storage.syncroom.app/backgrounds/work.jpg'    WHERE context = 'work';
UPDATE rooms SET background_picture = 'https://storage.syncroom.app/backgrounds/study.jpg'   WHERE context = 'study';
UPDATE rooms SET background_picture = 'https://storage.syncroom.app/backgrounds/sport.jpg'   WHERE context = 'sport';
