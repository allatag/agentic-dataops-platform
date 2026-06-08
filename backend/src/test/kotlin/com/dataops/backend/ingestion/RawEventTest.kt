package com.dataops.backend.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class RawEventTest {

    @Test
    fun `event has generated eventId and schemaVersion 1 by default`() {
        val event = RawEvent(
            tenantId = "tenant-a",
            source = "payment-service",
            eventType = "LATENCY_SPIKE",
            severity = "HIGH",
            occurredAt = Instant.now(),
            payload = mapOf("message" to "spike detected"),
        )

        assertNotNull(event.eventId)
        assertNotNull(event.correlationId)
        assertEquals(1, event.schemaVersion)
    }

    @Test
    fun `receivedAt is set automatically`() {
        val before = Instant.now()
        val event = RawEvent(
            tenantId = "tenant-a",
            source = "payment-service",
            eventType = "LATENCY_SPIKE",
            severity = "HIGH",
            occurredAt = Instant.now(),
            payload = emptyMap(),
        )
        val after = Instant.now()

        assertFalse(event.receivedAt.isBefore(before), "receivedAt should not be before test start")
        assertFalse(event.receivedAt.isAfter(after), "receivedAt should not be after test end")
    }
}
