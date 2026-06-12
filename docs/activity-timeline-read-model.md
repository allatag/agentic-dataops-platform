# Activity Timeline Read Model Design

## Goal

The activity timeline is the first derived data / CQRS read model over `raw_event`.
Its purpose is to make data-intensive design concerns visible before adding anomaly
detection, RAG retrieval, or CrewAI root cause analysis.

This workload borrows Twitter/social-feed-style characteristics such as high event
volume, skewed actor activity, hot keys, and time-ordered reads. The project is
not becoming a Twitter clone or a consumer social product. Activity events are a
synthetic workload used to exercise DDIA patterns in a backend platform.

## Raw Events vs Timeline Events

`raw_event` remains the durable source of accepted ingestion envelopes. It stores
the full payload for replay, audit, and future re-projection.

`activity_timeline` is a derived, query-oriented table. It stores the minimum
fields needed for bounded timeline reads without requiring every query to parse
raw JSON payloads or scan the source table.

The two tables have different responsibilities:

| Store | Responsibility | Optimized for |
|-------|----------------|---------------|
| `raw_event` | Durable source of all accepted events | ingestion, replay, audit, idempotent storage |
| `activity_timeline` | Denormalized projection of activity events | recent activity, actor timelines, filters, time windows |

## Event Vocabulary

The first activity vocabulary should stay small and synthetic:

- `post_created`
- `repost_created`
- `follow_created`
- `like_created`
- `timeline_viewed`
- `notification_clicked`

These event types are intentionally generic. They create enough variety to test
fanout decisions, actor-centric reads, object references, source/type filters,
and skew without introducing social graph implementation or product features.

## Proposed Table Shape

The first schema should be named `activity_timeline`.

Proposed columns:

| Column | Purpose |
|--------|---------|
| `id` | Database primary key for the projected row |
| `event_id` | Source `raw_event.event_id`; unique for idempotent projection |
| `tenant_id` | Tenant boundary for all timeline queries |
| `actor_id` | User, service, or synthetic actor that caused the activity |
| `source` | Originating system or synthetic workload source |
| `event_type` | Activity event type such as `post_created` |
| `object_id` | Primary object affected by the activity |
| `target_id` | Optional secondary object or recipient |
| `summary` | Compact human-readable event summary |
| `occurred_at` | Logical event timestamp from the raw event |
| `projected_at` | Time the derived row was written |

The projection should not copy the entire raw payload into this table in the
first version. If later query use cases need more structured fields, add them
intentionally through a schema migration rather than treating the timeline as a
second raw-event store.

## Query Use Cases

The first read API should support bounded queries only:

- Recent activity for a tenant, ordered newest first.
- Per-actor activity timeline, ordered newest first.
- Activity filtered by `event_type` and/or `source`.
- Time-window queries using `occurred_at` ranges.
- Combined tenant + actor + type/source filters for debugging and demos.

The read model does not need recommendation ranking, follower fanout, full-text
search, authorization, or cross-tenant aggregation in this phase.

## Indexing Plan

Initial indexes should match the query paths:

- Unique index on `event_id` for idempotent projection.
- Composite index on `(tenant_id, occurred_at desc)` for recent tenant activity.
- Composite index on `(tenant_id, actor_id, occurred_at desc)` for actor timelines.
- Composite index on `(tenant_id, event_type, occurred_at desc)` for type filters.
- Composite index on `(tenant_id, source, occurred_at desc)` for source filters.

This keeps the first version PostgreSQL-native and explicit. If future workload
measurements show different bottlenecks, indexes can be adjusted from observed
query plans instead of adding a separate search or cache system prematurely.

## Projection Semantics

The first implementation should project activity events after raw events have
been durably stored. `raw_event` remains the recovery point.

Expected behavior:

- Projection is eventually consistent with `raw_event`.
- A freshly accepted event may appear in `raw_event` before it appears in
  `activity_timeline`.
- `activity_timeline.event_id` is unique so duplicate delivery or replay does
  not create duplicate timeline rows.
- Reprocessing a raw event with the same `event_id` should skip the existing
  projection row or perform an idempotent upsert with the same values.
- Projection failures should be observable and retryable in a later issue, but
  this design issue does not implement failure handling.

For the first version, the projection can remain inside the existing Spring Boot
backend. A separate projection service is not needed until operational pressure
or ownership boundaries justify it.

## Replay and Backfill

Because the timeline is derived from `raw_event`, it should be rebuildable.
Future replay/backfill work should:

- Re-scan persisted raw events in deterministic timestamp order.
- Re-apply projection logic using `event_id` idempotency.
- Support replacing or backfilling timeline rows after schema changes.
- Keep a clear distinction between source event retention and derived view
  retention.

This is why the timeline stores compact query fields and keeps `raw_event` as the
source of truth.

## Why PostgreSQL in the Existing Backend

The first version stays inside the existing Spring Boot and PostgreSQL system
because the current learning goal is derived data modeling, not infrastructure
sprawl.

PostgreSQL is enough for:

- Versioned schema migrations.
- Transactional uniqueness on `event_id`.
- Composite indexes for bounded timeline reads.
- Local development and repeatable tests.
- Query-plan inspection before introducing specialized stores.

The following remain out of scope for this phase:

- Redis caching.
- Elasticsearch or OpenSearch.
- A separate timeline service.
- Frontend timelines.
- Authentication or social graph permissions.
- Recommendation systems.
- RAG, CrewAI, LangGraph, Google ADK, or agent workflows.

## Later AI and Incident Support

The timeline prepares future anomaly and incident context work without
implementing it now.

Later phases can derive:

- Anomaly candidates from unusual activity volume, hot actors, or source/type
  spikes.
- Incident context from the events around a time window.
- RAG retrieval inputs from compact summaries and linked raw events.
- CrewAI RCA evidence from persisted events, timelines, runbooks, and metrics.

Those later systems should consume the timeline as context. They should not
replace the durable event log or become the primary write model.
