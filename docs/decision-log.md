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
