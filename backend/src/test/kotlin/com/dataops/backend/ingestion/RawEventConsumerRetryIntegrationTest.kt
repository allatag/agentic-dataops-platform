package com.dataops.backend.ingestion

import com.dataops.backend.persistence.RawEventEntity
import com.dataops.backend.persistence.RawEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
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

    @Autowired
    lateinit var consumerFactory: ConsumerFactory<String, RawEvent>

    @Value("\${app.kafka.topic.raw-events-dlt}")
    lateinit var dltTopic: String

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

    @Test
    fun `consumer sends non retryable schema failure to DLT without persistence retries`() {
        val event = RawEvent(
            eventId = "non-retryable-event-id",
            correlationId = "non-retryable-correlation-id",
            schemaVersion = 2,
            tenantId = "tenant-a",
            source = "payment-service",
            eventType = "LATENCY_SPIKE",
            severity = "HIGH",
            occurredAt = Instant.now(),
            payload = mapOf("message" to "unsupported schema"),
        )
        val dltConsumer = consumerFactory.createConsumer("raw-event-consumer-retry-test-dlt", "test")
        dltConsumer.subscribe(listOf(dltTopic))

        try {
            kafkaTemplate.send("raw-events.v1", event.eventId, event).get(5, TimeUnit.SECONDS)

            val dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, dltTopic, Duration.ofSeconds(10))

            assertEquals(event.eventId, dltRecord.key())
            assertEquals(event, dltRecord.value())
            verify(repository, never()).save(any())
        } finally {
            dltConsumer.close()
        }
    }
}
