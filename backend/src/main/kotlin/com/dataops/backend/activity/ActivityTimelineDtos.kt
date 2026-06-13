package com.dataops.backend.activity

import java.time.Instant

data class ActivityTimelineQuery(
    val tenantId: String,
    val actorId: String? = null,
    val source: String? = null,
    val eventType: String? = null,
    val objectId: String? = null,
    val targetId: String? = null,
    val occurredFrom: Instant? = null,
    val occurredTo: Instant? = null,
    val limit: Int = DEFAULT_ACTIVITY_TIMELINE_LIMIT,
)

data class ActivityTimelineResponse(
    val limit: Int,
    val count: Int,
    val items: List<ActivityTimelineItemResponse>,
)

data class ActivityTimelineItemResponse(
    val eventId: String,
    val tenantId: String,
    val actorId: String,
    val source: String,
    val eventType: String,
    val objectId: String,
    val targetId: String?,
    val summary: String,
    val occurredAt: Instant,
    val projectedAt: Instant,
)

const val DEFAULT_ACTIVITY_TIMELINE_LIMIT = 50
const val MAX_ACTIVITY_TIMELINE_LIMIT = 100
