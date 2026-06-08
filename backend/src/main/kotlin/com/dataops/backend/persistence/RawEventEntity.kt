package com.dataops.backend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "raw_event")
class RawEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", nullable = false)
    val eventId: String,

    @Column(name = "schema_version", nullable = false)
    val schemaVersion: Int,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @Column(name = "source", nullable = false)
    val source: String,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(name = "severity", nullable = false)
    val severity: String,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,

    @Column(name = "payload_json", nullable = false)
    val payloadJson: String,
)
