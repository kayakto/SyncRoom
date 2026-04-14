CREATE TABLE task_like (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES study_tasks(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (task_id, user_id)
);

CREATE INDEX idx_task_like_task_id ON task_like(task_id);
CREATE INDEX idx_task_like_user_id ON task_like(user_id);
