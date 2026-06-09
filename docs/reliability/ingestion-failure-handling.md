# Ingestion Failure Handling Strategy

This document describes the current and planned behavior for failures in the raw event ingestion path:

```text
POST /api/events -> raw-events.v1 -> RawEventConsumer -> raw_event
```

The current implementation has a working happy path, Kafka-backed asynchronous ingestion, PostgreSQL persistence, idempotent duplicate handling with `raw_event.event_id`, correlated MDC logs, bounded consumer retries, and a raw event dead-letter topic. It does not yet define poison-message classification or replay behavior.

In this document, “DLQ” refers to a Kafka dead-letter topic (sometimes abbreviated “DLT”).
## Current Reliability Boundary

The application currently treats Kafka as the handoff point between HTTP ingestion and persistence. The HTTP endpoint waits for the Kafka broker acknowledgement before returning `202 Accepted`. After that, the raw event consumer is responsible for storing the event in PostgreSQL.

Current guarantees:

- Kafka producer sends with `acks: all` and waits for broker acknowledgement before the API returns.
- The consumer persists one `RawEvent` into `raw_event`.
- The database has a unique constraint on `event_id`.
- Duplicate `event_id` violations are caught and logged as duplicate skips.
- Other persistence exceptions are rethrown from the listener.
- Consumer log lines include MDC fields when a valid `RawEvent` reaches the listener.

Remaining gaps:

- Retry behavior is explicitly configured with a small fixed backoff.
- Failed `RawEvent` records are routed to `raw-events.v1.dlt` after retry exhaustion.
- Retryable and non-retryable failures are not classified yet.
- Malformed records may fail before the listener can attach event-specific MDC fields.
- Local verification for failure paths is mostly manual.

The DLT is named `raw-events.v1.dlt`: the source topic name plus `.dlt`. This keeps the source/DLT relationship visible in Kafka UI and leaves room for future versioned raw event topics to use the same convention.

## Retryable vs Non-Retryable Failures

Planned failure handling should divide failures into two groups.

Retryable failures are temporary infrastructure or persistence failures where the same message may succeed later:

- PostgreSQL unavailable.
- Transient database connection failure.
- Transient transaction or persistence failure.
- Unexpected runtime failure during consumer processing.

Non-retryable failures are poison messages where repeating the same input is not expected to help:

- Invalid JSON.
- Missing required raw event fields.
- Unsupported schema version.
- Payload cannot be deserialized into `RawEvent`.
- Consumer validation failure for the event envelope.

The planned behavior is modest local retry for retryable failures and dead-letter routing after retry exhaustion. Non-retryable messages should go to the dead-letter topic without wasting repeated retries once classification exists.

## Kafka Offset Commit Implications

Kafka tracks consumer progress through offsets. A message is not safely past the consumer until the listener work succeeds and the container can commit the offset.

In this project, the intended rule is:

- If persistence succeeds, the message can be acknowledged and the offset can advance.
- If duplicate persistence is detected through `event_id`, the duplicate skip is treated as successful processing and the offset can advance.
- If processing fails before offset commit, Kafka may redeliver the same message after retry, restart, or rebalance.
- If the consumer crashes after database persistence but before offset commit, redelivery can happen; the `event_id` unique constraint should make that redelivery idempotent.
- If the offset commits after successful persistence, Kafka will not normally redeliver that message to the same consumer group.

Until explicit retry and DLT configuration is added, framework defaults should not be treated as the project's designed failure policy.

## Failure Cases

### 1. Duplicate Kafka Message

Expected behavior:
Current: the consumer attempts to insert the event. PostgreSQL rejects the duplicate `event_id`, and the consumer catches the `uq_raw_event_event_id` violation. The duplicate is logged and skipped.
Planned: keep this behavior. Duplicate skips should remain successful processing and should include `eventId` and `tenantId` in logs.

Data-loss risk:
Low for the duplicate message because the original row already exists.

Duplicate-processing risk:
Low for `raw_event` rows because the unique constraint prevents duplicate storage. Side effects added later must also be idempotent.

Retry behavior:
No retry is needed for the known duplicate constraint violation.

DLQ:
No. A duplicate is not a poison message when the original event is already stored.

How to verify locally:
Publish the same Kafka message with the same `eventId` twice to `raw-events.v1` using Kafka UI or CLI. Verify only one row exists in `raw_event` and the consumer logs `Duplicate event - skipping` with MDC fields.

### 2. Consumer Receives Malformed Payload

Expected behavior:
Current: malformed JSON or a payload that cannot deserialize into `RawEvent` may fail before `RawEventConsumer.consume` is called. Event-specific MDC fields may not be available because no valid event envelope exists.
Planned: classify malformed payloads as non-retryable and route them to a dead-letter topic with enough metadata to inspect the original record.

Data-loss risk:
Current: low for investigation when the failed record can be deserialized as a `RawEvent`, because the original event is retained in `raw-events.v1.dlt` after retry exhaustion. Malformed records still need explicit deserialization error handling in a later issue.

Duplicate-processing risk:
Low, because malformed records should not be persisted.

Retry behavior:
Current: malformed record retry behavior is not fully project-defined because deserialization can fail before the listener receives a `RawEvent`.
Planned: no repeated retries after classification as malformed.

DLQ:
Current: only for failures that reach the raw event listener as `RawEvent`.
Planned: yes for malformed records after deserialization error handling is added.

How to verify locally:
Current: publish invalid JSON or an invalid `RawEvent` envelope to `raw-events.v1` and inspect backend logs.
Planned: verify the record appears in the raw event DLT after error classification and deserialization error handling are implemented.

### 3. Consumer Validation Failure

Expected behavior:
Current: the consumer does not run explicit validation beyond JSON deserialization and database constraints. Some invalid business values could still be persisted if they fit the database schema.
Planned: validate the consumed event envelope and classify validation failures as non-retryable poison messages.

Data-loss risk:
Current: invalid-but-deserializable data may enter `raw_event`.
Planned: invalid records should be retained in the DLT for inspection instead of being silently accepted.

Duplicate-processing risk:
Low for rejected records. If invalid records are currently persisted, later correction may require a migration or replay strategy.

Retry behavior:
Current: not explicitly project-defined.
Planned: no repeated retries for deterministic validation failures.

DLQ:
Current: no.
Planned: yes.

How to verify locally:
Current: publish an event envelope with a questionable but deserializable value and inspect whether it reaches `raw_event`.
Planned: publish an event that violates the consumer validation rules and verify it is sent to the DLT without repeated retries.

### 4. PostgreSQL Temporarily Unavailable

Expected behavior:
Current: `repository.save` throws a persistence exception. The consumer rethrows any non-duplicate persistence exception. Offset advancement should not be treated as successful when the listener fails.
Planned: classify the failure as retryable, retry with a small fixed backoff, and route to the DLT only after retry exhaustion.

Data-loss risk:
Current: low if the offset is not committed before successful processing, but behavior is not explicitly documented in application config.
Planned: low, with explicit retry and clear logs.

Duplicate-processing risk:
Moderate. If the database write eventually succeeds but offset commit does not, Kafka may redeliver the message. The `event_id` unique constraint should turn that redelivery into a duplicate skip.

Retry behavior:
Current: retryable with a modest fixed backoff, followed by DLT routing after retry exhaustion.
Planned: classify retryable infrastructure errors separately from deterministic data errors.

DLQ:
Current: yes, after retry exhaustion.

How to verify locally:
Current: stop PostgreSQL while the backend consumer is running, publish an event, and inspect backend logs. If retries exhaust before PostgreSQL is restored, verify the event appears in `raw-events.v1.dlt`.
Planned: add deterministic tests for retry classification and failure modes.

### 5. PostgreSQL Insert Fails After Kafka Message Is Consumed

Expected behavior:
Current: if insert fails before commit, the listener throws and the message is not considered successfully processed by application logic. If the insert committed but the listener or process fails before offset commit, the same Kafka message may be delivered again and should hit the duplicate skip path.
Planned: keep idempotency as the safety mechanism and make retry/DLT behavior explicit for the failing case.

Data-loss risk:
Low when the offset is not committed before the failed write is handled. Risk increases if failures are swallowed without persistence; the current consumer rethrows non-duplicate failures.

Duplicate-processing risk:
Moderate under crash or redelivery conditions. The unique `event_id` constraint mitigates duplicate rows.

Retry behavior:
Current: not explicitly project-defined.
Planned: retryable unless the insert failure is a known non-retryable data problem.

DLQ:
Current: no.
Planned: yes for failures that exhaust retry or are classified as non-retryable.

How to verify locally:
Current: simulate a repository failure in tests or make PostgreSQL unavailable during consumption.
Planned: add failure-mode tests proving retry, duplicate skip after partial success, and DLT routing.

### 6. Consumer Crashes Before Offset Commit

Expected behavior:
Current: Kafka can redeliver the message to the same consumer group after restart or rebalance.
Planned: preserve this at-least-once behavior and rely on idempotent persistence for safe redelivery.

Data-loss risk:
Low if the message remains in Kafka and the offset was not committed.

Duplicate-processing risk:
Moderate because the listener may see the same message again. Database idempotency prevents duplicate `raw_event` rows.

Retry behavior:
Current: restart/rebalance redelivery rather than explicit application retry.
Planned: explicit retry for listener failures, plus idempotency for crash recovery.

DLQ:
No for the crash itself. DLT applies if the message repeatedly fails after processing resumes.

How to verify locally:
Publish an event, stop the backend during or before processing, restart it, and inspect logs and `raw_event`. This is timing-sensitive today; later tests should cover the deterministic behavior.

### 7. Consumer Crashes After Successful Persistence

Expected behavior:
Current: if the database row is committed but the Kafka offset is not, redelivery may happen. The duplicate `event_id` path should skip the second insert. If the offset was committed before the crash, Kafka should not redeliver the message for the same group.
Planned: keep duplicate skip as the guardrail and document offset behavior in the reliability runbook.

Data-loss risk:
Low because the event is already stored when persistence succeeded.

Duplicate-processing risk:
Moderate for redelivery after persistence but before offset commit. The unique constraint mitigates duplicate rows.

Retry behavior:
No retry is needed once duplicate skip detects the already persisted event.

DLQ:
No, unless a later side effect fails repeatedly and cannot be classified as safe to skip.

How to verify locally:
Current: manually republish the same Kafka record after confirming it is stored in `raw_event`; verify duplicate skip.
Planned: add a deterministic failure-mode test for successful persistence followed by redelivery.

### 8. Poison Message Repeatedly Fails

Expected behavior:
Current: repeatedly failing `RawEvent` messages are retried with a fixed backoff and then sent to `raw-events.v1.dlt`. There is no explicit poison-message classification yet.
Planned: classify poison messages as non-retryable and route them to a DLT without unnecessary retries. Retryable messages should continue to go to the DLT after retry exhaustion.

Data-loss risk:
Current: low for valid `RawEvent` records that repeatedly fail, because they are retained in the DLT.
Planned: low for malformed and classified poison messages once classification is implemented.

Duplicate-processing risk:
Low for records that never persist. Moderate if a poison message fails after partial processing; future side effects must remain idempotent.

Retry behavior:
Current: bounded retry, then DLT.
Planned: no repeated retries for known non-retryable poison messages; retryable failures keep the bounded retry policy.

DLQ:
Current: yes, for failed records that reach the listener as `RawEvent`.
Planned: yes, including malformed records after deserialization error handling is added.

How to verify locally:
Current: publish a `RawEvent` that causes consumer processing to fail and verify it moves to `raw-events.v1.dlt` after retries.
Planned: publish a known poison message and verify it moves to the DLT without repeated retries.

## Planned Implementation Order

The planned reliability work should stay incremental:

1. Add explicit Kafka consumer retry behavior for retryable failures. Done.
2. Add a raw event dead-letter topic. Done.
3. Classify retryable and non-retryable consumer errors.
4. Add deterministic failure-mode tests.
5. Add a local reliability demo runbook.

This work intentionally comes before RAG, CrewAI, ReAct loops, or new infrastructure beyond the current Kafka/PostgreSQL backend.
