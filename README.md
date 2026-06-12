# agentic-dataops-platform

`agentic-dataops-platform` is a portfolio backend platform project focused on data-intensive infrastructure for agentic AI operations. It is intended to demonstrate Senior Backend, Platform, and AI Infrastructure engineering through an incremental system built around reliable event ingestion, persistence, and later production-oriented AI workflows.

This is not a chatbot project. The near-term focus is the data backbone: a Kotlin / Java Spring Boot backend, Kafka event ingestion, PostgreSQL persistence, local observability, explicit failure handling, and derived read models over high-volume events.

## Why This Exists

The project exists to show how agentic AI systems can be grounded in production-style backend architecture instead of being built only as prompt workflows. The long-term direction is to combine event-driven data ingestion, derived activity/event timelines, incident context retrieval, and multi-agent root cause analysis while keeping the underlying data model and reliability patterns explicit.

The project will use Twitter/social-feed-style workload characteristics as a practical example of data volume, skew, and time-ordered reads. It is not intended to become a Twitter clone. The activity workload exists to make DDIA concepts concrete before later anomaly detection, RAG, and CrewAI workflows are added.

## Target Architecture

The implemented ingestion flow is:

```text
HTTP API -> Kafka -> Consumer -> PostgreSQL
```

Implemented components:

- `POST /api/events` — HTTP ingestion API with validation.
- Kafka producer — publishes versioned `RawEvent` envelopes to `raw-events.v1`.
- Kafka consumer — reads from `raw-events.v1` and persists events to PostgreSQL.
- Kafka retry and dead-letter handling — retryable consumer failures use bounded retry and failed raw event records are routed to `raw-events.v1.dlt`.
- Poison-message handling — deterministic envelope validation failures are classified as non-retryable and routed to the DLT without repeated retries.
- `raw_event` table — stores all event fields with a unique constraint on `event_id` for idempotency.
- Failure-mode tests — cover retry, DLT routing, poison-message classification, and duplicate-event behavior.
- Flyway migrations — versioned schema management.

## Current Phase

Current phase: High-volume activity timeline / CQRS read model.

The ingestion backbone, observability baseline, and local reliability/failure-handling phase are complete. See [`docs/week-1-summary.md`](docs/week-1-summary.md), [`docs/ingestion-baseline-load-test.md`](docs/ingestion-baseline-load-test.md), and [`docs/reliability/ingestion-failure-handling.md`](docs/reliability/ingestion-failure-handling.md) for the implemented behavior and DDIA concepts applied.

Completed reliability behavior:

- Actuator metrics are exposed for Prometheus and visualized through the local Grafana dashboard.
- Ingestion logs include MDC correlation fields.
- Duplicate Kafka deliveries remain idempotent through the `raw_event.event_id` unique constraint.
- Retryable consumer failures use bounded retry before DLT routing.
- Non-retryable poison messages are classified and sent to `raw-events.v1.dlt`.
- Failure-mode tests cover retry success, retry exhaustion, DLT routing, poison messages, and duplicate events.

Next implementation focus: build an activity timeline read model over persisted raw events.

The planned data path is:

```text
high-volume activity events
  -> Kafka raw log
  -> raw_event durable store
  -> activity timeline / CQRS projection
  -> query-friendly time-ordered views
  -> later anomaly or incident candidates
  -> later RAG / CrewAI RCA
```

The first activity workload should stay synthetic and backend-focused. Example event types may include `post_created`, `repost_created`, `follow_created`, `like_created`, `timeline_viewed`, and `notification_clicked`. These events are useful because they exercise append logs, denormalized read models, high-cardinality filters, hot-key/skew discussions, eventual consistency, idempotent projection, and future replay/backfill.

The initial read-model design is documented in [`docs/activity-timeline-read-model.md`](docs/activity-timeline-read-model.md).

## Long-Term Roadmap

- Phase 1: Event ingestion backbone with HTTP API, Kafka, consumer, PostgreSQL persistence, and idempotency.
- Phase 2: Observability and baseline ingestion with Spring Actuator, Prometheus, Grafana, k6, and MDC correlation.
- Phase 3: Ingestion reliability and failure handling with idempotency, bounded retry, DLT routing, poison-message classification, and failure-mode tests.
- Next: High-volume activity timeline / CQRS read model over persisted raw events.
- Later: Anomaly or incident candidates derived from event/activity timelines.
- Later: RAG context retrieval for runbooks, incident memory, and supporting documents.
- Later: CrewAI-based root cause analysis workflow.
- Later: ReAct-style tool usage and self-reflection / critic loop.
- Phase 7: Production hardening with tracing, metrics, evaluation, fallback behavior, and security notes.

Future RAG, CrewAI, ReAct, and self-reflection phases are not implemented yet.

## How to Run

### Local infrastructure

Requires Docker and Docker Compose.

```bash
# Start PostgreSQL, Kafka, Kafka UI, Prometheus, and Grafana
docker compose up -d

# Stop services without removing containers or volumes (preserves Kafka/PostgreSQL data)
docker compose stop

# Remove containers (volumes are preserved)
docker compose down

# Remove containers and volumes (wipes all data)
docker compose down -v
```

Services:

| Service    | URL / address          |
|------------|------------------------|
| PostgreSQL | `localhost:5432`       |
| Kafka      | `localhost:9092`       |
| Kafka UI   | http://localhost:8090  |
| Prometheus | http://localhost:9090  |
| Grafana    | http://localhost:3000  |

PostgreSQL credentials: database `dataops`, user `dataops`, password `dataops`.

Grafana credentials: user `dataops`, password `dataops`.
Provisioned dashboard: `DataOps / Ingestion Demo`.

Prometheus scrapes the host-run backend at `http://host.docker.internal:8080/actuator/prometheus`.
Start the backend with `./gradlew bootRun` before checking the Prometheus `backend` target.

### Backend

Requires Java 17.

```bash
cd backend
./gradlew bootRun
```

The service starts on `http://localhost:8080`.

Health check: `http://localhost:8080/actuator/health`

Prometheus metrics: `http://localhost:8080/actuator/prometheus`

Console logs include MDC fields for ingestion correlation:
`correlationId`, `tenantId`, `eventId`, `source`, and `eventType`.
Send `X-Correlation-Id` on `POST /api/events` to reuse a caller-provided value; otherwise the backend generates one.

To build and run tests:

```bash
./gradlew build
```

### Baseline ingestion load test

Run the baseline ingestion demo from the repository root:

```bash
k6 run scripts/ingestion-baseline.k6.js
```

See [`docs/ingestion-baseline-load-test.md`](docs/ingestion-baseline-load-test.md) for the full runbook, Grafana checks, PostgreSQL verification, and reset commands.

### Reliability and failure handling

The current ingestion failure-handling strategy is documented in [`docs/reliability/ingestion-failure-handling.md`](docs/reliability/ingestion-failure-handling.md). It describes the implemented idempotency, bounded retry, dead-letter topic (DLT), poison-message classification, and remaining replay/deserialization gaps.

For a hands-on local walkthrough of happy-path ingestion, duplicate handling, retry behavior, DLT routing, PostgreSQL checks, Kafka UI inspection, and MDC log correlation, see [`docs/runbooks/ingestion-reliability-demo.md`](docs/runbooks/ingestion-reliability-demo.md).

## Manual Verification

This section shows how to verify the ingestion flow end-to-end.

### Prerequisites

Docker Compose services are running and the backend is started (see above).

### Observability demo

1. Start local infrastructure with `docker compose up -d`.
2. Start the backend from `backend/` with `./gradlew bootRun`.
3. Open Grafana at `http://localhost:3000` and sign in with `dataops` / `dataops`.
4. Open **Dashboards > DataOps > Ingestion Demo**.
5. Send events to `POST /api/events` using the command below.

The dashboard should show the backend as up, JVM/runtime metrics, and request activity for `POST /api/events`.
During repeated requests, watch ingestion throughput, request latency, and response status panels.

### 1. Send an ingestion event

```bash
curl -s -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-a",
    "source": "payment-service",
    "eventType": "LATENCY_SPIKE",
    "severity": "HIGH",
    "message": "P95 latency increased above threshold"
  }'
```

Expected response: `202 Accepted` (empty body).

### 2. Verify the event in Kafka UI

Open `http://localhost:8090`, navigate to **Topics → raw-events.v1 → Messages**.

The event envelope should appear with `eventId`, `schemaVersion`, `tenantId`, and `payload`.

Dead-letter topic naming: failed records from `raw-events.v1` are sent to `raw-events.v1.dlt`. The DLT keeps the source topic name and appends `.dlt`, which makes the relationship obvious in Kafka UI.

To inspect the DLT in Kafka UI, open `http://localhost:8090`, navigate to **Topics → raw-events.v1.dlt → Messages**. For CLI inspection:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1.dlt \
  --from-beginning \
  --max-messages 5
```

### 3. Verify the event in PostgreSQL

```bash
psql -h localhost -U dataops -d dataops -c "SELECT event_id, tenant_id, event_type, severity, received_at FROM raw_event ORDER BY created_at DESC LIMIT 5;"
```

### 4. Verify Kafka-level duplicate suppression

Each HTTP request generates a new `eventId` UUID, so repeating the HTTP call produces a distinct event — that is by design. To verify the idempotency guard, replay the same Kafka message twice (e.g., from Kafka UI, re-publish the same message on `raw-events.v1`). The consumer logs `"Duplicate event … — skipping"` and only one row is stored in `raw_event`.

### 5. Validation error

```bash
curl -s -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "tenant-a"}'
```

Expected response: `400 Bad Request` with validation errors for missing required fields.

## Troubleshooting

**Docker Compose services not starting:**
- Ensure Docker Desktop is running.
- Run `docker compose logs` to see startup errors.
- Kafka uses KRaft mode (no ZooKeeper); if port 9092 or 8090 is in use, stop conflicting processes.

**Backend fails to connect to Kafka or PostgreSQL:**
- Ensure Docker Compose services are healthy: `docker compose ps`.
- The backend expects Kafka at `localhost:9092` and PostgreSQL at `localhost:5432`.

**Prometheus cannot scrape the backend:**
- Ensure the backend is running on the host with `./gradlew bootRun`.
- Check `http://localhost:8080/actuator/prometheus` from the host.
- In Prometheus, open `Status > Targets` and verify the `backend` target.

**Flyway migration fails:**
- Check that the `dataops` database exists and the user has DDL privileges.
- Run `docker compose down -v && docker compose up -d` to reset volumes and re-run migrations.
