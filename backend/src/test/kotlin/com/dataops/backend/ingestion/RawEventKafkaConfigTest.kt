package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcKeys
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.concurrent.CompletableFuture

class RawEventKafkaConfigTest {

    private val kafkaTemplate = mock<KafkaTemplate<String, RawEvent>>()
    private val config = RawEventKafkaConfig()

    private val event = RawEvent(
        eventId = "event-1",
        correlationId = "correlation-1",
        tenantId = "tenant-a",
        source = "payment-service",
        eventType = "LATENCY_SPIKE",
        severity = "HIGH",
        occurredAt = Instant.parse("2026-06-09T10:00:00Z"),
        payload = mapOf("message" to "P95 latency spike"),
    )

    @Test
    fun `recoverer publishes failed raw event to DLT with original key`() {
        whenever(kafkaTemplate.send(any(), any<String>(), any()))
            .thenReturn(CompletableFuture.completedFuture(null))
        val recoverer = config.rawEventDltRecoverer(kafkaTemplate, "raw-events.v1.dlt")
        val record = ConsumerRecord("raw-events.v1", 0, 42L, "tenant-a", event)

        recoverer.accept(record, IllegalStateException("boom"))

        val topicCaptor = argumentCaptor<String>()
        val keyCaptor = argumentCaptor<String>()
        val eventCaptor = argumentCaptor<RawEvent>()
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture())

        assertEquals("raw-events.v1.dlt", topicCaptor.firstValue)
        assertEquals("tenant-a", keyCaptor.firstValue)
        assertEquals(event, eventCaptor.firstValue)
    }

    @Test
    fun `recoverer clears event MDC after publishing to DLT`() {
        whenever(kafkaTemplate.send(any(), any<String>(), any()))
            .thenAnswer {
                assertEquals("correlation-1", MDC.get(MdcKeys.CORRELATION_ID))
                assertEquals("event-1", MDC.get(MdcKeys.EVENT_ID))
                assertEquals("tenant-a", MDC.get(MdcKeys.TENANT_ID))
                CompletableFuture.completedFuture(null)
            }
        val recoverer = config.rawEventDltRecoverer(kafkaTemplate, "raw-events.v1.dlt")
        val record = ConsumerRecord("raw-events.v1", 0, 42L, "tenant-a", event)

        recoverer.accept(record, IllegalStateException("boom"))

        assertNull(MDC.get(MdcKeys.CORRELATION_ID))
        assertNull(MDC.get(MdcKeys.EVENT_ID))
        assertNull(MDC.get(MdcKeys.TENANT_ID))
        assertNull(MDC.get(MdcKeys.SOURCE))
        assertNull(MDC.get(MdcKeys.EVENT_TYPE))
    }
}
