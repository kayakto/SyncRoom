ALTER TABLE bot_user
    ALTER COLUMN config TYPE TEXT
    USING config::text;
