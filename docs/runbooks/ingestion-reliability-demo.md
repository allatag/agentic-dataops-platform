# Ingestion Reliability Demo Runbook

This runbook shows how to demonstrate the current local ingestion reliability behavior:

```text
POST /api/events -> raw-events.v1 -> RawEventConsumer -> raw_event
```

It is a local reliability demo, not a production-grade reliability claim. The current system has bounded retry, idempotent duplicate handling for `event_id`, and a Kafka dead-letter topic for failed `RawEvent` records that reach the listener.

## Prerequisites

- Docker Desktop is running.
- Java 17 is available.
- `curl` is available.
- Commands are run from the repository root unless a step says otherwise.

Start local infrastructure:

```bash
docker compose up -d
docker compose ps
```

Start the backend in a second terminal:

```bash
cd backend
./gradlew bootRun
```

Wait for the backend health endpoint to report `UP`:

```bash
curl -s http://localhost:8080/actuator/health
```

Useful local addresses:

| Service | Address |
| --- | --- |
| Backend | http://localhost:8080 |
| Kafka UI | http://localhost:8090 |
| PostgreSQL | `localhost:5432` |
| Kafka | `localhost:9092` |

## 1. Happy-Path Ingestion

Send a valid event through the HTTP API:

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-happy-correlation-1" \
  -d '{
    "tenantId": "tenant-demo",
    "source": "payment-service",
    "eventType": "LATENCY_SPIKE",
    "severity": "HIGH",
    "message": "P95 latency increased above threshold"
  }'
```

Expected result:

- HTTP response is `202 Accepted`.
- Backend logs show the request correlation ID and event fields:
  `correlationId`, `tenantId`, `eventId`, `source`, and `eventType`.
- Kafka UI shows a message in **Topics > raw-events.v1 > Messages**.
- PostgreSQL stores one row in `raw_event`.

Verify the latest rows in PostgreSQL:

```bash
docker compose exec postgres psql -U dataops -d dataops \
  -c "SELECT event_id, tenant_id, source, event_type, severity, received_at FROM raw_event ORDER BY created_at DESC LIMIT 5;"
```

## 2. Duplicate Event Handling

HTTP ingestion generates a new `eventId` for each accepted request, so repeat HTTP calls are separate events. To demonstrate the idempotency guard, publish the same Kafka `RawEvent` envelope twice with the same `eventId`.

Create the raw topic if the backend has not already created it:

```bash
docker compose exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --create \
  --if-not-exists \
  --topic raw-events.v1 \
  --partitions 1 \
  --replication-factor 1
```

Publish the same event twice:

```bash
printf '%s\n%s\n' \
'{"eventId":"demo-duplicate-event-1","correlationId":"demo-duplicate-correlation-1","schemaVersion":1,"tenantId":"tenant-demo","source":"kafka-cli","eventType":"DUPLICATE_DEMO","severity":"LOW","occurredAt":"2026-06-12T10:00:00Z","receivedAt":"2026-06-12T10:00:01Z","payload":{"message":"duplicate demo"}}' \
'{"eventId":"demo-duplicate-event-1","correlationId":"demo-duplicate-correlation-1","schemaVersion":1,"tenantId":"tenant-demo","source":"kafka-cli","eventType":"DUPLICATE_DEMO","severity":"LOW","occurredAt":"2026-06-12T10:00:00Z","receivedAt":"2026-06-12T10:00:01Z","payload":{"message":"duplicate demo"}}' \
| docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1
```

Expected result:

- The first message is persisted.
- The second message is skipped as a duplicate.
- Backend logs include `Duplicate event - skipping` with `eventId=demo-duplicate-event-1`.
- PostgreSQL contains one row for `demo-duplicate-event-1`.

Verify the duplicate row count:

```bash
docker compose exec postgres psql -U dataops -d dataops \
  -c "SELECT event_id, COUNT(*) FROM raw_event WHERE event_id = 'demo-duplicate-event-1' GROUP BY event_id;"
```

## 3. Non-Retryable Poison Message and DLT Routing

Publish a `RawEvent` envelope with an unsupported schema version. This reaches the listener, is classified as non-retryable, and is sent to the dead-letter topic without repeated retries.

```bash
printf '%s\n' \
'{"eventId":"demo-poison-event-1","correlationId":"demo-poison-correlation-1","schemaVersion":2,"tenantId":"tenant-demo","source":"kafka-cli","eventType":"POISON_DEMO","severity":"HIGH","occurredAt":"2026-06-12T10:05:00Z","receivedAt":"2026-06-12T10:05:01Z","payload":{"message":"unsupported schema version demo"}}' \
| docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1
```

Expected result:

- Backend logs show `Unsupported raw event schemaVersion=2`.
- Logs show DLT routing with `classification=NON_RETRYABLE`.
- The event is not inserted into `raw_event`.
- Kafka UI shows the event in **Topics > raw-events.v1.dlt > Messages**.

Inspect the DLT from the CLI:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1.dlt \
  --from-beginning \
  --max-messages 5
```

Verify the poison event was not persisted:

```bash
docker compose exec postgres psql -U dataops -d dataops \
  -c "SELECT COUNT(*) FROM raw_event WHERE event_id = 'demo-poison-event-1';"
```

## 4. Retryable Consumer Failure and DLT Routing

Use this path to demonstrate bounded retry for a retryable infrastructure failure. The simplest local trigger is stopping PostgreSQL while the backend consumer is running.

Stop PostgreSQL:

```bash
docker compose stop postgres
```

Publish a valid event directly to Kafka:

```bash
printf '%s\n' \
'{"eventId":"demo-retry-event-1","correlationId":"demo-retry-correlation-1","schemaVersion":1,"tenantId":"tenant-demo","source":"kafka-cli","eventType":"RETRY_DEMO","severity":"MEDIUM","occurredAt":"2026-06-12T10:10:00Z","receivedAt":"2026-06-12T10:10:01Z","payload":{"message":"postgres unavailable demo"}}' \
| docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1
```

Expected result:

- Backend logs show retry attempts for the same record.
- Retry uses `app.kafka.consumer.retry.max-attempts: 3` and `app.kafka.consumer.retry.backoff: 1s`.
- If PostgreSQL remains stopped until retries are exhausted, logs show DLT routing with `classification=RETRYABLE`.
- Kafka UI shows the event in **Topics > raw-events.v1.dlt > Messages**.

Start PostgreSQL again before continuing:

```bash
docker compose up -d postgres
```

Inspect DLT records:

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic raw-events.v1.dlt \
  --from-beginning \
  --max-messages 10
```

## 5. Kafka UI Inspection

Open Kafka UI at http://localhost:8090.

Check these topics:

- **Topics > raw-events.v1 > Messages** for accepted raw events.
- **Topics > raw-events.v1.dlt > Messages** for failed `RawEvent` records.

For each message, inspect:

- `eventId`
- `correlationId`
- `schemaVersion`
- `tenantId`
- `eventType`
- `payload`

## 6. Log Correlation Checks

Backend logs are expected to include MDC fields in the console pattern:

```text
correlationId=... tenantId=... eventId=... source=... eventType=...
```

Useful checks:

- For the happy path, confirm `correlationId=demo-happy-correlation-1`.
- For duplicate handling, confirm `eventId=demo-duplicate-event-1` and `Duplicate event - skipping`.
- For poison-message DLT routing, confirm `eventId=demo-poison-event-1` and `classification=NON_RETRYABLE`.
- For retryable DLT routing, confirm `eventId=demo-retry-event-1` and `classification=RETRYABLE`.

Malformed JSON may fail before a `RawEvent` exists, so event-specific MDC fields may be unavailable for that case.

## 7. Reset Local State

Stop services without deleting data:

```bash
docker compose stop
```

Remove containers but preserve volumes:

```bash
docker compose down
```

Remove containers and volumes, wiping Kafka and PostgreSQL data:

```bash
docker compose down -v
```

Start from an empty local state:

```bash
docker compose down -v
docker compose up -d
```

Then restart the backend:

```bash
cd backend
./gradlew bootRun
```
