package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcKeys
import com.dataops.backend.persistence.RawEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class RawEventConsumerTest {

    private val repository = org.mockito.kotlin.mock<RawEventRepository>()
    private val objectMapper = ObjectMapper()
    private val consumer = RawEventConsumer(repository, objectMapper)

    private val event = RawEvent(
        eventId = "test-event-id",
        correlationId = "test-correlation-id",
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
    fun `consumer sets event MDC while handling and clears it afterwards`() {
        doAnswer {
            assertEquals("test-correlation-id", MDC.get(MdcKeys.CORRELATION_ID))
            assertEquals("test-event-id", MDC.get(MdcKeys.EVENT_ID))
            assertEquals("tenant-a", MDC.get(MdcKeys.TENANT_ID))
            assertEquals("payment-service", MDC.get(MdcKeys.SOURCE))
            assertEquals("LATENCY_SPIKE", MDC.get(MdcKeys.EVENT_TYPE))
            it.arguments[0]
        }.whenever(repository).save(any())

        consumer.consume(event)

        assertNull(MDC.get(MdcKeys.CORRELATION_ID))
        assertNull(MDC.get(MdcKeys.EVENT_ID))
        assertNull(MDC.get(MdcKeys.TENANT_ID))
        assertNull(MDC.get(MdcKeys.SOURCE))
        assertNull(MDC.get(MdcKeys.EVENT_TYPE))
    }

    @Test
    fun `duplicate event is silently skipped`() {
        doThrow(DataIntegrityViolationException(
            """duplicate key value violates unique constraint "uq_raw_event_event_id""""
        )).whenever(repository).save(any())

        // Should not throw
        consumer.consume(event)

        verify(repository, times(1)).save(any())
    }

    @Test
    fun `unsupported schema version is rejected before persistence`() {
        val invalidEvent = event.copy(schemaVersion = 2)

        val exception = assertThrows(NonRetryableRawEventException::class.java) {
            consumer.consume(invalidEvent)
        }

        assertEquals("Unsupported raw event schemaVersion=2", exception.message)
        verify(repository, times(0)).save(any())
    }

    @Test
    fun `blank correlation id is rejected before persistence`() {
        val invalidEvent = event.copy(correlationId = "")

        val exception = assertThrows(NonRetryableRawEventException::class.java) {
            consumer.consume(invalidEvent)
        }

        assertEquals("Raw event is missing correlationId", exception.message)
        verify(repository, times(0)).save(any())
    }

    @Test
    fun `blank severity is rejected before persistence`() {
        val invalidEvent = event.copy(severity = "")

        val exception = assertThrows(NonRetryableRawEventException::class.java) {
            consumer.consume(invalidEvent)
        }

        assertEquals("Raw event is missing severity", exception.message)
        verify(repository, times(0)).save(any())
    }

    @Test
    fun `duplicate event is skipped before schema validation`() {
        val duplicateEvent = event.copy(schemaVersion = 2)
        whenever(repository.existsByEventId(duplicateEvent.eventId)).thenReturn(true)

        consumer.consume(duplicateEvent)

        verify(repository, times(1)).existsByEventId(duplicateEvent.eventId)
        verify(repository, times(0)).save(any())
    }
}
