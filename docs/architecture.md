# Architecture

## Current Scope

The current phase is Week 1 - Event ingestion backbone.

The target flow for Week 1 is:

```text
HTTP API -> Kafka -> Consumer -> PostgreSQL
```

The intended responsibilities are:

- HTTP API: accept versioned raw event envelopes.
- Kafka: act as the append-style ingestion backbone.
- Consumer: read raw events from Kafka and persist them.
- PostgreSQL: store durable raw events for later query, replay, and incident workflows.

No backend service, Kafka topic configuration, consumer, database schema, or Docker Compose setup has been implemented yet.

## Near-Term Architecture

Week 1 should stay intentionally simple:

- One backend application.
- One Kafka topic for raw events: `raw-events.v1`.
- One PostgreSQL table for raw events: `raw_event`.
- Documentation of reliability and schema evolution choices as they are made.

The first working system should prove this path:

```text
POST /api/events
    -> raw-events.v1
    -> raw event consumer
    -> raw_event
```

## Future Phases

Later phases may add:

- RAG context retrieval over runbooks, incident memory, and operational documents.
- CrewAI-based root cause analysis agents.
- ReAct-style tool usage for evidence gathering.
- Self-reflection / critic loops for evidence checking and confidence calibration.

These future phases are not implemented yet. They should be added only after the event ingestion backbone and data reliability foundation are in place.
