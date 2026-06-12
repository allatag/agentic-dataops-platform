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

## 0003 - Use an Activity Timeline Workload for Derived Data

### Context

After the ingestion, observability, and reliability phases, the next step needs to demonstrate data-intensive application design rather than simple CRUD over incidents. A plain incident tracker would not create enough pressure around event volume, time-ordered reads, derived state, skew, replay, or eventual consistency.

### Decision

Use an activity-feed-style workload to drive the first derived data / CQRS read model.

The target direction is:

```text
high-volume activity events
    -> Kafka raw log
    -> raw_event durable store
    -> activity timeline / CQRS projection
    -> query-friendly time-ordered views
    -> later anomaly or incident candidates
    -> later RAG / CrewAI RCA
```

The activity vocabulary may use examples such as `post_created`, `repost_created`, `follow_created`, `like_created`, `timeline_viewed`, and `notification_clicked`, but the project is not a Twitter clone.

### Reasoning

Twitter/social-feed-style workloads make DDIA concepts concrete: append-only event logs, denormalized read models, fanout trade-offs, hot keys, high-cardinality filters, time-window queries, eventual consistency, replay, and backfill.

This keeps the project aligned with Senior Backend / Platform / AI Infrastructure positioning while still preparing useful context for later anomaly detection, RAG retrieval, and CrewAI root-cause analysis.

### Trade-Offs

The activity workload is less directly tied to incident management than an incident CRUD model, but it is a stronger data-intensive example. Incident candidates can be derived later from the event stream once the platform has queryable timelines and aggregate context.

The first implementation should stay inside the existing Spring Boot/PostgreSQL/Kafka architecture. Redis, Elasticsearch, social graph fanout, recommendation systems, frontend, auth, RAG, and CrewAI remain out of scope until separately justified.
