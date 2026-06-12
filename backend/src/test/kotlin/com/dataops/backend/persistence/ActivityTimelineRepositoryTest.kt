package com.dataops.backend.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@DataJpaTest
class ActivityTimelineRepositoryTest(
    @Autowired private val repository: ActivityTimelineRepository,
) {

    @Test
    fun `activity timeline row can be persisted`() {
        val entity = activityTimelineEntity(eventId = "event-1")

        val saved = repository.saveAndFlush(entity)

        assertTrue(saved.id > 0)
        assertTrue(repository.existsByEventId("event-1"))
        assertEquals("post_created", saved.eventType)
    }

    @Test
    fun `duplicate source event id is rejected`() {
        repository.saveAndFlush(activityTimelineEntity(eventId = "event-duplicate"))

        assertThrows(DataIntegrityViolationException::class.java) {
            repository.saveAndFlush(activityTimelineEntity(eventId = "event-duplicate"))
        }
    }

    private fun activityTimelineEntity(eventId: String) = ActivityTimelineEntity(
        eventId = eventId,
        tenantId = "tenant-a",
        actorId = "actor-123",
        source = "activity-generator",
        eventType = "post_created",
        objectId = "post-456",
        targetId = null,
        summary = "actor-123 created post-456",
        occurredAt = Instant.parse("2026-06-13T10:15:30Z"),
        projectedAt = Instant.parse("2026-06-13T10:15:31Z"),
    )
}
