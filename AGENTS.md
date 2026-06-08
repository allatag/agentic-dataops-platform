# AGENTS.md

Repository-level guidance for Codex, Claude, and other AI assistants.

## Project

Repository: `allatag/agentic-dataops-platform`

Project name: `agentic-dataops-platform`

This is a portfolio project for Senior Backend / Platform / AI Infrastructure roles.

The goal is to build a production-style data-intensive backend platform that combines:

* Kotlin / Java Spring Boot backend
* Kafka event ingestion
* PostgreSQL persistence
* DDIA-inspired reliability and data modeling patterns
* Later: RAG context retrieval
* Later: CrewAI-based agentic RCA workflow
* Later: ReAct-style tool usage and self-reflection / critic loop

This is not a chatbot project. It should demonstrate backend, distributed systems, data-intensive architecture, and production-oriented AI workflows.

---

## Current phase

We are currently in:

**Week 1 — Event ingestion backbone**

The target flow for Week 1 is:

```text
HTTP API → Kafka → Consumer → PostgreSQL
```

Do not implement CrewAI, RAG, LangGraph, Google ADK, Kubernetes, Terraform, frontend, auth, or complex microservice architecture yet.

---

## Week 1 goal

By the end of Week 1, the repository should contain:

```text
agentic-dataops-platform/
├── README.md
├── AGENTS.md
├── docker-compose.yml
├── backend/
├── docs/
│   ├── architecture.md
│   ├── ddia-notes.md
│   ├── decision-log.md
│   └── week-1-summary.md
└── scripts/
```

And a working flow:

```text
POST /api/events
    ↓
Kafka topic: raw-events.v1
    ↓
Kafka consumer
    ↓
PostgreSQL table: raw_event
```

---

## Architecture constraints

Keep the architecture simple and incremental.

Allowed now:

* Kotlin
* Spring Boot
* Kafka
* PostgreSQL
* Docker Compose
* Flyway or Liquibase
* JUnit
* Testcontainers if useful
* Markdown documentation

Not allowed yet:

* CrewAI
* RAG
* LangGraph
* Google ADK
* Kubernetes
* Terraform
* Redis
* frontend
* authentication
* complex microservice split
* production cloud deployment
* unnecessary abstractions

---

## Long-term roadmap

### Phase 1 — Event ingestion backbone

Build the reliable data backbone:

* HTTP ingestion API
* versioned event envelope
* Kafka topic
* Kafka consumer
* PostgreSQL persistence
* idempotency
* retry/DLQ later
* schema evolution notes

### Phase 2 — Data-intensive reliability

Apply DDIA concepts:

* reliability
* scalability
* maintainability
* schema evolution
* event logs
* replay
* idempotency
* consistency trade-offs

### Phase 3 — Anomaly and incident context

Add:

* anomaly events
* incident history
* event search APIs
* mock metrics/logs/runbooks
* preparation for RAG tools

### Phase 4 — RAG context retrieval

Add:

* document ingestion
* runbook storage
* incident memory
* vector search
* retrieval tools for agents

### Phase 5 — CrewAI RCA workflow

Add agents:

* Incident Classifier Agent
* Context Retriever Agent
* Root Cause Analyst Agent
* Risk Reviewer Agent
* Action Plan Writer Agent

### Phase 6 — ReAct and self-reflection

Add:

* ReAct-style tool usage loop
* Reflection / Critic Agent
* evidence checking
* confidence calibration
* retry loop when evidence is weak

### Phase 7 — Production hardening

Add:

* tracing
* metrics
* token/cost logging
* eval dataset
* fallback behavior
* security notes
* prompt injection risk notes

---

## Session rules

* Work on **one ticket per session**. When a user lists multiple issues, implement only the first one, push the branch, open the PR, and stop. Ask the user which issue to tackle next in a new session.
* Do not chain multiple issues in a single session even if asked. Doing so bloats the context window, makes review harder, and increases the risk of errors across unrelated changes.
* Always branch off `main` and open the PR against `main`. This ensures GitHub links the PR to the issue and closes it automatically on merge. Never stack PRs against other feature branches.

---

## Coding rules

* Make small, focused changes.
* Implement only the current issue.
* Do not rewrite unrelated files.
* Do not introduce new technologies without explicit approval.
* Prefer clear package structure over over-engineering.
* Keep `main` stable.
* Every implementation should match the GitHub issue scope.
* Implement only the linked issue scope; do not expand beyond requested acceptance criteria.
* If a task is ambiguous, ask for clarification instead of inventing scope.
* Do not add future placeholder code unless the issue explicitly asks for it.

### Kotlin / Spring Boot rules

* Always include `kotlin("plugin.jpa")` in `build.gradle.kts` whenever Spring Data JPA is used. Without it, Hibernate cannot create proxies or no-arg constructors for `@Entity` classes.
* For PostgreSQL with `ddl-auto: validate`, annotate all `String` JPA columns with `columnDefinition = "TEXT"` to match the Flyway migration type and prevent schema validation failures.
* Disable Kafka listener containers in unit tests: set `spring.kafka.listener.auto-startup: false` in `src/test/resources/application.yml`. This prevents test context startup failures when no broker is running.
* Never use `KafkaTemplate.send()` as fire-and-forget. Wait for the broker acknowledgement with a bounded timeout before returning `202 Accepted`; in Kotlin, prefer an explicit `runCatching { future.orTimeout(...).join() }.getOrElse { ... }` flow that unwraps `CompletionException` and rethrows with useful context.
* Set `producer.acks: all` in `application.yml` so every send requires full ISR acknowledgement before returning.
* Kotlin DTO fields used with `@Valid`/`@NotBlank` must have default values (e.g., `= ""`). Without them, Jackson throws a deserialization error before Bean Validation runs, resulting in a generic parse error instead of structured field validation errors.
* Use `Map<String, Any?>` for generic event payload fields, not `Map<String, String>`, to allow numeric, boolean, and nested values without schema changes.
* Use JUnit assertions (`assertTrue`, `assertFalse`, `assertEquals`) in tests, not Kotlin `assert()`. Kotlin `assert()` is a no-op unless the JVM is started with `-ea`.
* When catching `DataIntegrityViolationException` for idempotency, always check `ex.mostSpecificCause.message` for the specific constraint name and rethrow for any other violation. A broad catch silently drops legitimate errors.

### Docker Compose / Kafka rules

* Always configure dual Kafka listeners in Docker Compose: one for host clients (`PLAINTEXT_EXTERNAL://localhost:<port>`) and one for Docker-network clients (`PLAINTEXT_INTERNAL://kafka:<internal-port>`). With only `localhost` in `KAFKA_ADVERTISED_LISTENERS`, any service inside Docker (Kafka UI, future consumers) will receive `localhost` in metadata responses and fail to connect.
* Pin all Docker image tags to a specific version. Never use `latest`; it makes builds non-reproducible.
* Add a named volume for Kafka data (`/var/lib/kafka/data`) so topic data survives container restarts. Document `docker compose stop` (non-destructive) alongside `docker compose down` (removes containers) and `docker compose down -v` (removes volumes).
* Set `backend/gradlew` as executable (`git update-index --chmod=+x backend/gradlew`) when committing the Gradle wrapper. Without mode `100755`, `./gradlew` fails with `Permission denied` on Unix.

---

## Git rules

For every task:

* Work on a dedicated branch.
* Use focused commits.
* Do not work directly on `main`.
* Do not mix docs, infra, and backend changes unless the issue requires it.
* Before finishing, run relevant tests or explain why they could not be run.
* Leave the working tree clean.
* Open a PR with a clear description.

Branch naming examples:

```text
docs/initialize-repo-structure
feature/docker-compose-infra
feature/event-ingestion-api
feature/kafka-producer
feature/raw-event-consumer
feature/idempotent-event-persistence
```

---

## Pull request format

Every PR should include:

```markdown
## What changed

- ...

## Why

- ...

## How tested

- ...

## Notes

- ...
```

If the PR closes an issue, include:

```markdown
Closes #<issue-number>
```

---

## Documentation rules

Update documentation only when behavior, architecture, or setup changes.

Important files:

* `README.md` — project overview and how to run
* `docs/architecture.md` — system design
* `docs/decision-log.md` — architecture decisions
* `docs/ddia-notes.md` — DDIA concept → project application
* `docs/week-1-summary.md` — weekly progress summary

Do not write long book summaries. DDIA notes should always follow this format:

```markdown
## Topic: Reliability

### DDIA idea

...

### Application in this project

...

### Design implication

...

### Open question

...
```

---

## Review guidelines

When reviewing PRs, focus on:

* scope creep
* unnecessary technologies
* broken project structure
* missing tests
* missing setup instructions
* Kafka/idempotency/transaction boundary issues
* whether the change supports a Senior Backend / Platform / AI Infrastructure portfolio story

---

## Project positioning

This project should not be described as:

```text
A CrewAI chatbot.
```

It should be described as:

```text
A production-style data-intensive agentic AI platform combining Kafka-based event ingestion, PostgreSQL persistence, RAG context retrieval, CrewAI multi-agent RCA workflows, ReAct-style tool usage, self-reflection, tracing, evaluation, and DDIA-inspired reliability patterns.
```
