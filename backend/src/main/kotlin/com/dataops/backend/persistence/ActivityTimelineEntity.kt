package com.dataops.backend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "activity_timeline",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_activity_timeline_event_id",
            columnNames = ["event_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_activity_timeline_tenant_occurred_at", columnList = "tenant_id, occurred_at"),
        Index(name = "idx_activity_timeline_tenant_actor_occurred_at", columnList = "tenant_id, actor_id, occurred_at"),
        Index(name = "idx_activity_timeline_tenant_event_type_occurred_at", columnList = "tenant_id, event_type, occurred_at"),
        Index(name = "idx_activity_timeline_tenant_source_occurred_at", columnList = "tenant_id, source, occurred_at"),
    ],
)
class ActivityTimelineEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id", nullable = false, columnDefinition = "TEXT")
    val eventId: String,

    @Column(name = "tenant_id", nullable = false, columnDefinition = "TEXT")
    val tenantId: String,

    @Column(name = "actor_id", nullable = false, columnDefinition = "TEXT")
    val actorId: String,

    @Column(name = "source", nullable = false, columnDefinition = "TEXT")
    val source: String,

    @Column(name = "event_type", nullable = false, columnDefinition = "TEXT")
    val eventType: String,

    @Column(name = "object_id", nullable = false, columnDefinition = "TEXT")
    val objectId: String,

    @Column(name = "target_id", columnDefinition = "TEXT")
    val targetId: String? = null,

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    val summary: String,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @Column(name = "projected_at", nullable = false)
    val projectedAt: Instant,
)
