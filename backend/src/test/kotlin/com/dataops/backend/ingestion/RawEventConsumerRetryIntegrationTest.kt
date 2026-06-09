package com.dataops.backend.ingestion

import com.dataops.backend.persistence.RawEventEntity
import com.dataops.backend.persistence.RawEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(
    properties = [
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=raw-event-consumer-retry-test",
        "spring.kafka.listener.auto-startup=true",
        "app.kafka.consumer.retry.max-attempts=3",
        "app.kafka.consumer.retry.backoff=10ms",
    ],
)
@EmbeddedKafka(partitions = 1, topics = ["raw-events.v1"])
@DirtiesContext
class RawEventConsumerRetryIntegrationTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, RawEvent>

    @MockitoBean
    lateinit var repository: RawEventRepository

    @Test
    fun `consumer retries transient persistence failure and eventually persists`() {
        val attempts = AtomicInteger(0)
        doAnswer { invocation ->
            if (attempts.incrementAndGet() < 3) {
                throw TransientDataAccessResourceException("temporary database connectivity failure")
            }
            invocation.arguments[0]
        }.whenever(repository).save(any())

        val event = RawEvent(
            eventId = "retry-event-id",
            correlationId = "retry-correlation-id",
            tenantId = "tenant-a",
            source = "payment-service",
            eventType = "LATENCY_SPIKE",
            severity = "HIGH",
            occurredAt = Instant.now(),
            payload = mapOf("message" to "P95 latency spike"),
        )

        kafkaTemplate.send("raw-events.v1", event.eventId, event).get(5, TimeUnit.SECONDS)

        val persistedEvents = argumentCaptor<RawEventEntity>()
        verify(repository, timeout(10_000).times(3)).save(persistedEvents.capture())
        assertEquals(3, attempts.get())
        assertEquals(listOf(event.eventId), persistedEvents.allValues.map { it.eventId }.distinct())
    }
}
