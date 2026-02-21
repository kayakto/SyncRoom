-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    provider VARCHAR(32) NOT NULL,
    provider_id VARCHAR(255),
    avatar_url TEXT,
    password_hash VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_provider_provider_id ON users(provider, provider_id) WHERE provider_id IS NOT NULL;

-- Add comment to table
COMMENT ON TABLE users IS 'Users table for SyncRoom application';
COMMENT ON COLUMN users.provider IS 'Authentication provider: vk, yandex, or email';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password for email provider';
