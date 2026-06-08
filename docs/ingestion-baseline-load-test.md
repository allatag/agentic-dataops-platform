# Baseline Ingestion Load Test

This runbook documents the local Week 2 ingestion demo for issue #27. It is a modest repeatable baseline, not a benchmark or stress test.

## Tooling Decision

Use `k6` for the baseline load test. It is purpose-built for HTTP traffic generation, supports fixed arrival rates, and reports success/error/latency metrics without adding code to the backend.

## Prerequisites

- Docker Desktop or Docker Engine is running.
- `k6` is installed and available on `PATH`.
- Java 17 is available for the backend.

Start the local infrastructure from the repository root:

```bash
docker compose up -d
```

Start the backend in a second terminal:

```bash
cd backend
./gradlew bootRun
```

Confirm the backend is healthy:

```bash
curl http://localhost:8080/actuator/health
```

## Baseline Scenario

The script sends valid `POST /api/events` requests with varied tenants, sources, event types, and severities.

Traffic shape:

- Warmup: 1 request per second for 30 seconds.
- Baseline: 5 requests per second for 2 minutes.
- Expected volume: about 630 accepted events.
- Expected success rate: at least 99% `202 Accepted` responses on a healthy local setup.

Run from the repository root:

```bash
k6 run scripts/ingestion-baseline.k6.js
```

To point at a different backend URL:

```bash
BASE_URL=http://localhost:8081 k6 run scripts/ingestion-baseline.k6.js
```

## What To Watch In Grafana

Open Grafana at `http://localhost:3000` and sign in with `dataops` / `dataops`.

Open **Dashboards > DataOps > Ingestion Demo** before starting the k6 run.

During the run, watch:

- Backend target is up.
- Request throughput rises during warmup, then holds near 5 RPS during the baseline phase.
- `POST /api/events` status distribution is dominated by `202`.
- Request latency remains stable rather than growing throughout the run.
- JVM memory and CPU/runtime panels do not show continuous unbounded growth.

Prometheus scrapes every 15 seconds, so dashboard panels may lag the k6 output slightly.

## Verify Persistence In PostgreSQL

Check the total stored events:

```bash
docker exec dataops-postgres psql -U dataops -d dataops -c "SELECT count(*) FROM raw_event;"
```

Check the latest ingested rows:

```bash
docker exec dataops-postgres psql -U dataops -d dataops -c "SELECT tenant_id, source, event_type, severity, received_at FROM raw_event ORDER BY created_at DESC LIMIT 10;"
```

If the database was empty before the run, the count should increase by roughly the expected event volume. If the table already had data, compare the count before and after the run.

## Reset Data

To clear only persisted events while keeping containers and volumes:

```bash
docker exec dataops-postgres psql -U dataops -d dataops -c "TRUNCATE TABLE raw_event;"
```

To stop services while preserving data:

```bash
docker compose stop
```

To remove containers while preserving named volumes:

```bash
docker compose down
```

To remove containers and all local PostgreSQL, Kafka, Prometheus, and Grafana volumes:

```bash
docker compose down -v
```

Use the volume reset when you need a completely clean local demo state, including Kafka topic data and Grafana/Prometheus local state.
