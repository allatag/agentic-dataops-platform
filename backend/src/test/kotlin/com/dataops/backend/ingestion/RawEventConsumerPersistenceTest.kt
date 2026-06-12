package com.dataops.backend.ingestion

import com.dataops.backend.persistence.ActivityTimelineRepository
import com.dataops.backend.persistence.RawEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class RawEventConsumerPersistenceTest(
    @Autowired private val consumer: RawEventConsumer,
    @Autowired private val rawEventRepository: RawEventRepository,
    @Autowired private val activityTimelineRepository: ActivityTimelineRepository,
) {

    @BeforeEach
    fun cleanDatabase() {
        activityTimelineRepository.deleteAll()
        rawEventRepository.deleteAll()
    }

    @Test
    fun `activity event consumption persists raw event and timeline projection`() {
        val event = activityEvent()

        consumer.consume(event)

        val projection = activityTimelineRepository.findByEventId(event.eventId)
        assertEquals(1, rawEventRepository.count())
        assertEquals(1, activityTimelineRepository.count())
        assertNotNull(projection)
        assertEquals("user-123", projection!!.actorId)
        assertEquals("post-456", projection.objectId)
        assertEquals("feed-789", projection.targetId)
        assertEquals("user-123 created post-456", projection.summary)
    }

    @Test
    fun `duplicate activity event replay does not duplicate raw event or timeline projection`() {
        val event = activityEvent()

        consumer.consume(event)
        consumer.consume(event)

        assertEquals(1, rawEventRepository.count())
        assertEquals(1, activityTimelineRepository.count())
    }

    @Test
    fun `invalid activity projection rolls back raw event persistence`() {
        val event = activityEvent(payload = mapOf("actorId" to "user-123"))

        assertThrows(NonRetryableRawEventException::class.java) {
            consumer.consume(event)
        }

        assertEquals(0, rawEventRepository.count())
        assertEquals(0, activityTimelineRepository.count())
    }

    private fun activityEvent(
        payload: Map<String, Any?> = mapOf(
            "actorId" to "user-123",
            "objectId" to "post-456",
            "targetId" to "feed-789",
            "summary" to "user-123 created post-456",
        ),
    ) = RawEvent(
        eventId = "activity-event-1",
        correlationId = "activity-correlation-1",
        tenantId = "tenant-a",
        source = "activity-generator",
        eventType = "post_created",
        severity = "INFO",
        occurredAt = Instant.parse("2026-06-13T10:15:30Z"),
        payload = payload,
    )
}
