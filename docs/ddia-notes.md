# DDIA Notes

## Topic: Reliability

### DDIA idea

Reliable systems continue to work correctly even when faults occur. Faults can happen in networks, services, disks, processes, or human operations.

### Application in this project

The Week 1 ingestion path should avoid treating the HTTP request and database write as the only important boundary. Kafka gives the system a durable intermediate log so accepted events can be consumed and persisted independently.

### Design implication

The platform should make delivery behavior, idempotency, retries, and failure handling explicit as the ingestion flow is implemented.

### Open question

What idempotency key should the raw event envelope use to prevent duplicate database rows when events are retried or replayed?

## Topic: Scalability

### DDIA idea

Scalable systems can handle growth in data volume, traffic, or complexity by making bottlenecks visible and designing components that can evolve.

### Application in this project

Kafka separates event intake from persistence work. That gives the project a path to scale consumers, manage backpressure, and add new downstream processors later without changing the initial producer contract.

### Design implication

The raw event topic and database schema should be versioned early enough to support later schema evolution and replay.

### Open question

What partitioning key should be used for raw events so ordering is useful without forcing all events through one partition?

## Topic: Maintainability

### DDIA idea

Maintainable systems are understandable, operable, and adaptable over time. Clear boundaries and explicit decisions reduce accidental complexity.

### Application in this project

The project should keep Week 1 focused on one ingestion backbone instead of introducing agents, RAG, orchestration frameworks, or cloud deployment too early.

### Design implication

Documentation should track architecture decisions as behavior is added, and implementation should prefer simple package boundaries over premature service splits.

### Open question

Which contracts should be documented first: the HTTP request shape, the Kafka event envelope, or the PostgreSQL raw event schema?
