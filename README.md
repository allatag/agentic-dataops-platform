# agentic-dataops-platform

`agentic-dataops-platform` is a portfolio backend platform project focused on data-intensive infrastructure for agentic AI operations. It is intended to demonstrate Senior Backend, Platform, and AI Infrastructure engineering through an incremental system built around reliable event ingestion, persistence, and later production-oriented AI workflows.

This is not a chatbot project. The near-term focus is the data backbone: a Kotlin / Java Spring Boot backend, Kafka event ingestion, PostgreSQL persistence, and local observability that shows how the ingestion flow behaves.

## Why This Exists

The project exists to show how agentic AI systems can be grounded in production-style backend architecture instead of being built only as prompt workflows. The long-term direction is to combine event-driven data ingestion, incident context retrieval, and multi-agent root cause analysis while keeping the underlying data model and reliability patterns explicit.

## Target Architecture

The Week 1 target flow is:

```text
HTTP API -> Kafka -> Consumer -> PostgreSQL
```

Week 1 components:

- `POST /api/events` — HTTP ingestion API with validation.
- Kafka producer — publishes versioned `RawEvent` envelopes to `raw-events.v1`.
- Kafka consumer — reads from `raw-events.v1` and persists events to PostgreSQL.
- `raw_event` table — stores all event fields with a unique constraint on `event_id` for idempotency.
- Flyway migrations — versioned schema management.

## Current Phase

Current phase: Week 2 - Observability and baseline ingestion demo.

Week 1 event ingestion is complete. See [`docs/week-1-summary.md`](docs/week-1-summary.md) for a full summary of implemented work and DDIA concepts applied.

Current observability/baseline issue sequence:

1. [#24](https://github.com/allatag/agentic-dataops-platform/issues/24) Expose backend Prometheus metrics via Spring Actuator.
2. [#25](https://github.com/allatag/agentic-dataops-platform/issues/25) Add Prometheus and Grafana local observability stack.
3. [#26](https://github.com/allatag/agentic-dataops-platform/issues/26) Provision an ingestion demo Grafana dashboard.
4. [#27](https://github.com/allatag/agentic-dataops-platform/issues/27) Add baseline ingestion load test and results runbook.
5. [#29](https://github.com/allatag/agentic-dataops-platform/issues/29) Add MDC correlation context for ingestion logs.

## Long-Term Roadmap

- Phase 1: Event ingestion backbone with HTTP API, Kafka, consumer, PostgreSQL persistence, and idempotency.
- Phase 2: Data-intensive reliability patterns inspired by DDIA, including schema evolution, replay, and consistency trade-offs.
- Phase 3: Anomaly and incident context APIs with mock operational data.
- Phase 4: RAG context retrieval for runbooks, incident memory, and supporting documents.
- Phase 5: CrewAI-based root cause analysis workflow.
- Phase 6: ReAct-style tool usage and self-reflection / critic loop.
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

To build and run tests:

```bash
./gradlew build
```

## Manual Verification

This section shows how to verify the full Week 1 ingestion flow end-to-end.

### Prerequisites

Docker Compose services are running and the backend is started (see above).

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
