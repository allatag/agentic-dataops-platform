package com.dataops.backend.ingestion

import com.dataops.backend.persistence.ActivityTimelineEntity
import com.dataops.backend.persistence.ActivityTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessResourceException
import java.time.Instant

class ActivityTimelineProjectionServiceTest {

    private val repository = org.mockito.kotlin.mock<ActivityTimelineRepository>()
    private val service = ActivityTimelineProjectionService(repository)

    @Test
    fun `activity event is projected to timeline row`() {
        val event = activityEvent(
            payload = mapOf(
                "actorId" to "user-123",
                "objectId" to "post-456",
                "targetId" to "feed-789",
                "summary" to "user-123 created post-456",
            ),
        )

        service.project(event)

        val projection = argumentCaptor<ActivityTimelineEntity>()
        verify(repository).saveAndFlush(projection.capture())
        assertEquals(event.eventId, projection.firstValue.eventId)
        assertEquals("tenant-a", projection.firstValue.tenantId)
        assertEquals("user-123", projection.firstValue.actorId)
        assertEquals("activity-generator", projection.firstValue.source)
        assertEquals("post_created", projection.firstValue.eventType)
        assertEquals("post-456", projection.firstValue.objectId)
        assertEquals("feed-789", projection.firstValue.targetId)
        assertEquals("user-123 created post-456", projection.firstValue.summary)
        assertEquals(event.occurredAt, projection.firstValue.occurredAt)
    }

    @Test
    fun `missing optional activity fields still creates timeline row`() {
        val event = activityEvent(
            payload = mapOf(
                "actorId" to "user-123",
                "objectId" to "post-456",
            ),
        )

        service.project(event)

        val projection = argumentCaptor<ActivityTimelineEntity>()
        verify(repository).saveAndFlush(projection.capture())
        assertNull(projection.firstValue.targetId)
        assertEquals("user-123 post_created post-456", projection.firstValue.summary)
    }

    @Test
    fun `non activity event is ignored`() {
        val event = activityEvent(eventType = "LATENCY_SPIKE")

        service.project(event)

        verify(repository, never()).existsByEventId(any())
        verify(repository, never()).saveAndFlush(any())
    }

    @Test
    fun `missing required activity field is rejected`() {
        val event = activityEvent(
            payload = mapOf("actorId" to "user-123"),
        )

        val exception = assertThrows(NonRetryableRawEventException::class.java) {
            service.project(event)
        }

        assertEquals("Activity event activity-event-1 is missing required payload field objectId", exception.message)
        verify(repository, never()).saveAndFlush(any())
    }

    @Test
    fun `duplicate projection constraint violation is skipped`() {
        val event = activityEvent()
        doThrow(
            DataIntegrityViolationException(
                """duplicate key value violates unique constraint "uq_activity_timeline_event_id"""",
            ),
        ).whenever(repository).saveAndFlush(any())

        service.project(event)

        verify(repository).saveAndFlush(any())
    }

    @Test
    fun `projection persistence failure is propagated`() {
        val event = activityEvent()
        doThrow(TransientDataAccessResourceException("database unavailable"))
            .whenever(repository).saveAndFlush(any())

        val exception = assertThrows(TransientDataAccessResourceException::class.java) {
            service.project(event)
        }

        assertEquals("database unavailable", exception.message)
    }

    private fun activityEvent(
        eventType: String = "post_created",
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
        eventType = eventType,
        severity = "INFO",
        occurredAt = Instant.parse("2026-06-13T10:15:30Z"),
        payload = payload,
    )
}
