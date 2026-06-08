# Week 1 Summary

## Goal

Build the reliable data ingestion backbone:

```text
POST /api/events → Kafka (raw-events.v1) → Consumer → PostgreSQL (raw_event)
```

## Completed Work

### Infrastructure

- Added `docker-compose.yml` with PostgreSQL, Kafka (KRaft, no ZooKeeper), and Kafka UI.
- PostgreSQL runs at `localhost:5432`, Kafka at `localhost:9092`, Kafka UI at `http://localhost:8090`.

### Backend skeleton

- Created Kotlin / Spring Boot 3.5 project under `backend/` using Gradle Kotlin DSL.
- Java 17 toolchain, Actuator health endpoint at `/actuator/health`.

### Event ingestion API

- `POST /api/events` accepts a versioned event payload and returns `202 Accepted`.
- Request DTO validated with `@NotBlank` constraints; invalid requests return `400 Bad Request`.
- Fields: `tenantId`, `source`, `eventType`, `severity`, `message`.

### Kafka producer

- Valid ingestion requests are published to Kafka topic `raw-events.v1`.
- Event envelope fields: `eventId` (UUID), `schemaVersion=1`, `tenantId`, `source`, `eventType`, `severity`, `occurredAt`, `receivedAt`, `payload`.
- Partition key is `tenantId` for per-tenant ordering.

### Kafka consumer and PostgreSQL persistence

- Kafka consumer group `raw-event-consumer` listens to `raw-events.v1`.
- Flyway migration `V1` creates the `raw_event` table with all envelope fields.
- Each consumed event is persisted as a row in `raw_event`.

### Idempotency

- Flyway migration `V2` adds a `UNIQUE` constraint on `raw_event.event_id`.
- Consumer catches `DataIntegrityViolationException` on duplicate `eventId` and skips without crashing.
- Demonstrates awareness of at-least-once delivery and the need for idempotent consumers.

### Tests

7 tests passing:

| Test | Purpose |
|------|---------|
| `contextLoads` | Spring context starts cleanly |
| `valid request returns 202 Accepted` | Controller + mock producer |
| `missing required field returns 400 Bad Request` | Validation |
| `new event is persisted` | Consumer happy path |
| `duplicate event is silently skipped` | Idempotency guard |
| `event has generated eventId and schemaVersion 1 by default` | Envelope defaults |
| `receivedAt is set automatically` | Envelope timestamp |

## DDIA Concepts Applied

| Concept | Application |
|---------|-------------|
| **Reliability** | Kafka as durable intermediate log; HTTP acceptance and DB persistence are decoupled |
| **Scalability** | `tenantId` partition key; consumer group allows horizontal scaling later |
| **Maintainability** | Versioned event envelope (`schemaVersion`); Flyway migrations; focused package structure |
| **Idempotency** | Unique constraint on `event_id`; consumer skips duplicates rather than crashing |

## Architecture

```text
┌─────────────────────┐
│  HTTP Client        │
│  POST /api/events   │
└────────┬────────────┘
         │ 202 Accepted
         ▼
┌─────────────────────┐
│  IngestionController│
│  @Valid DTO binding │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  IngestionProducer  │
│  KafkaTemplate      │
└────────┬────────────┘
         │ key=tenantId
         ▼
┌─────────────────────┐
│  Kafka              │
│  raw-events.v1      │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  RawEventConsumer   │
│  @KafkaListener     │
└────────┬────────────┘
         │ UNIQUE(event_id)
         ▼
┌─────────────────────┐
│  PostgreSQL         │
│  raw_event table    │
└─────────────────────┘
```

## Open Questions for Week 2

- Should `occurredAt` be client-supplied or always set at receive time?
- Should the consumer use a transactional outbox for guaranteed delivery, or is at-least-once with idempotency sufficient for this phase?
- What partitioning strategy is needed once event volume increases beyond a single topic?

## Next Steps (Week 2 candidates)

- Schema evolution: add nullable fields to `RawEvent` with backward-compatible Flyway migrations.
- Event replay: allow consumers to reprocess events from a given offset.
- Anomaly events: add a second event type with richer payload structure.
- Observability: structured logging with correlation IDs, Actuator metrics.
- Testcontainers: replace H2 with a real PostgreSQL container for integration tests.
