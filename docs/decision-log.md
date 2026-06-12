# Decision Log

## 0001 - Use Kafka as the Event-Driven Backbone

### Context

The platform needs a reliable way to ingest operational events before later phases add incident context retrieval and agentic root cause analysis. The first milestone is a Week 1 backbone that moves raw events from an HTTP API through Kafka into PostgreSQL.

### Decision

Use an event-driven architecture with Kafka as the central ingestion backbone.

The initial target flow is:

```text
HTTP API -> Kafka -> Consumer -> PostgreSQL
```

### Reasoning

Kafka provides a durable event log that fits the project's data-intensive architecture goals. It supports decoupling event producers from persistence consumers, gives the system a foundation for replay, and creates a natural place to discuss schema evolution, idempotency, ordering, and delivery semantics.

This is also a better fit for the project positioning than starting with agent code. The platform should first demonstrate a production-style data backbone that later AI workflows can rely on.

### Trade-Offs

Kafka adds operational complexity compared with writing directly from the HTTP API to PostgreSQL. It requires local infrastructure, topic management, serialization decisions, and careful consumer behavior.

For this project, the trade-off is acceptable because the goal is to demonstrate backend and platform engineering patterns, not only the shortest path to storing a request.

## 0002 - Handle Consumer Failures Before Introducing AI Layers

### Context

The project will eventually add RAG context retrieval and agentic root cause analysis, but those workflows depend on trustworthy operational data. The ingestion path needed explicit behavior for duplicate delivery, transient persistence failures, poison messages, and dead-letter routing first.

### Decision

Handle consumer failures before introducing AI layers.

The ingestion reliability phase defines:

- idempotent duplicate handling with `raw_event.event_id`;
- bounded retry for retryable consumer failures;
- `raw-events.v1.dlt` for failed `RawEvent` records;
- non-retryable poison-message classification;
- failure-mode tests for retry, DLT, poison-message, and duplicate behavior.

### Reasoning

Agentic RCA is only useful if the underlying ingestion system has explicit behavior for duplicates, transient failures, poison messages, and dead-letter routing.

### Trade-Offs

This delays RAG and CrewAI work, but it keeps the data backbone honest. Later AI workflows can build on known delivery and failure semantics instead of hiding reliability gaps behind prompt orchestration.
