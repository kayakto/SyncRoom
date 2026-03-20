CREATE TABLE gartic_chains (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id  UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
    UNIQUE(game_id, owner_id)
);

CREATE INDEX idx_gartic_chains_game ON gartic_chains(game_id);

CREATE TABLE gartic_steps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_id    UUID NOT NULL REFERENCES gartic_chains(id) ON DELETE CASCADE,
    player_id   UUID NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    step_type   VARCHAR(16) NOT NULL, -- TEXT, DRAWING
    content     TEXT NOT NULL,
    UNIQUE(chain_id, step_number)
);

CREATE INDEX idx_gartic_steps_chain ON gartic_steps(chain_id);

