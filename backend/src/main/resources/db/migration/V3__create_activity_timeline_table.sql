CREATE TABLE activity_timeline (
    id           BIGSERIAL PRIMARY KEY,
    event_id     TEXT        NOT NULL,
    tenant_id    TEXT        NOT NULL,
    actor_id     TEXT        NOT NULL,
    source       TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    object_id    TEXT        NOT NULL,
    target_id    TEXT,
    summary      TEXT        NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_activity_timeline_event_id UNIQUE (event_id)
);

CREATE INDEX idx_activity_timeline_tenant_occurred_at
    ON activity_timeline (tenant_id, occurred_at DESC);

CREATE INDEX idx_activity_timeline_tenant_actor_occurred_at
    ON activity_timeline (tenant_id, actor_id, occurred_at DESC);

CREATE INDEX idx_activity_timeline_tenant_event_type_occurred_at
    ON activity_timeline (tenant_id, event_type, occurred_at DESC);

CREATE INDEX idx_activity_timeline_tenant_source_occurred_at
    ON activity_timeline (tenant_id, source, occurred_at DESC);
