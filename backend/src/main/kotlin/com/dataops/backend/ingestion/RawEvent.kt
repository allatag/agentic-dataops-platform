package com.dataops.backend.ingestion

import java.time.Instant
import java.util.UUID

data class RawEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = 1,
    val tenantId: String,
    val source: String,
    val eventType: String,
    val severity: String,
    val occurredAt: Instant,
    val receivedAt: Instant = Instant.now(),
    val payload: Map<String, String>,
)
