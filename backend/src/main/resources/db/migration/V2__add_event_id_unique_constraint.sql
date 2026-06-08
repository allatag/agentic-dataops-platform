ALTER TABLE raw_event
    ADD CONSTRAINT uq_raw_event_event_id UNIQUE (event_id);
