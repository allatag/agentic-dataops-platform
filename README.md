# agentic-dataops-platform

`agentic-dataops-platform` is a portfolio backend platform project focused on data-intensive infrastructure for agentic AI operations. It is intended to demonstrate Senior Backend, Platform, and AI Infrastructure engineering through an incremental system built around reliable event ingestion, persistence, and later production-oriented AI workflows.

This is not a chatbot project. The near-term focus is the data backbone: a Kotlin / Java Spring Boot backend, Kafka event ingestion, and PostgreSQL persistence.

## Why This Exists

The project exists to show how agentic AI systems can be grounded in production-style backend architecture instead of being built only as prompt workflows. The long-term direction is to combine event-driven data ingestion, incident context retrieval, and multi-agent root cause analysis while keeping the underlying data model and reliability patterns explicit.

## Target Architecture

The Week 1 target flow is:

```text
HTTP API -> Kafka -> Consumer -> PostgreSQL
```

Planned Week 1 components:

- HTTP ingestion API for raw events.
- Kafka topic for append-style event transport.
- Consumer process for reading raw events.
- PostgreSQL table for durable persistence.

## Current Phase

Current phase: Week 1 - Event ingestion backbone.

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
# Start PostgreSQL, Kafka, and Kafka UI
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

PostgreSQL credentials: database `dataops`, user `dataops`, password `dataops`.

### Backend

Requires Java 17.

```bash
cd backend
./gradlew bootRun
```

The service starts on `http://localhost:8080`.

Health check: `http://localhost:8080/actuator/health`

To build and run tests:

```bash
./gradlew build
```
