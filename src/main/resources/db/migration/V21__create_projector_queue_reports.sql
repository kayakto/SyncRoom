CREATE TABLE projector_queue_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_item_id UUID NOT NULL REFERENCES projector_queue_items(id) ON DELETE CASCADE,
    reporter_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(queue_item_id, reporter_id)
);

CREATE INDEX idx_projector_queue_reports_item ON projector_queue_reports(queue_item_id);
