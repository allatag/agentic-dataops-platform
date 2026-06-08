CREATE TABLE raw_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    TEXT        NOT NULL,
    schema_version INT      NOT NULL,
    tenant_id   TEXT        NOT NULL,
    source      TEXT        NOT NULL,
    event_type  TEXT        NOT NULL,
    severity    TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
