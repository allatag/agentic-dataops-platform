package com.dataops.backend.ingestion

import com.dataops.backend.persistence.RawEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class RawEventConsumerTest {

    private val repository = org.mockito.kotlin.mock<RawEventRepository>()
    private val objectMapper = ObjectMapper()
    private val consumer = RawEventConsumer(repository, objectMapper)

    private val event = RawEvent(
        eventId = "test-event-id",
        tenantId = "tenant-a",
        source = "payment-service",
        eventType = "LATENCY_SPIKE",
        severity = "HIGH",
        occurredAt = Instant.now(),
        payload = mapOf("message" to "P95 latency spike"),
    )

    @Test
    fun `new event is persisted`() {
        consumer.consume(event)
        verify(repository, times(1)).save(any())
    }

    @Test
    fun `duplicate event is silently skipped`() {
        doThrow(DataIntegrityViolationException("duplicate key")).whenever(repository).save(any())

        // Should not throw
        consumer.consume(event)

        verify(repository, times(1)).save(any())
    }
}
