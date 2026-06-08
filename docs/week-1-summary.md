# Week 1 Summary

## Planned Work

- Initialize repository structure and project documentation.
- Add local infrastructure for Kafka and PostgreSQL.
- Add backend application skeleton.
- Implement `POST /api/events`.
- Publish accepted events to Kafka topic `raw-events.v1`.
- Consume raw events from Kafka.
- Persist consumed events to PostgreSQL table `raw_event`.
- Add focused tests for the ingestion path where practical.

## Completed Work

- Repository structure initialization is in progress.

## Next Steps

- Add Docker Compose for local Kafka and PostgreSQL.
- Create the backend project skeleton.
- Define the initial raw event envelope.
