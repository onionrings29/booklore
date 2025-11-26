-- Create user_ephemera_settings table for per-user ephemera configuration
CREATE TABLE user_ephemera_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    server_ip VARCHAR(255),
    server_port INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_ephemera_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_ephemera_settings UNIQUE (user_id),
    CONSTRAINT check_server_port CHECK (server_port IS NULL OR (server_port >= 1 AND server_port <= 65535))
);

-- Create index for faster lookups
CREATE INDEX idx_user_ephemera_settings_user_id ON user_ephemera_settings(user_id);
CREATE INDEX idx_user_ephemera_settings_enabled ON user_ephemera_settings(enabled);
