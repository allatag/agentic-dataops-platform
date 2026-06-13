package com.dataops.backend.persistence

import com.dataops.backend.activity.ActivityTimelineQuery
import com.dataops.backend.activity.ActivityTimelineSpecifications
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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

    @Test
    fun `activity timeline query filters by tenant actor source event object target and time range`() {
        repository.saveAllAndFlush(
            listOf(
                activityTimelineEntity(eventId = "match", occurredAt = Instant.parse("2026-06-13T10:15:30Z")),
                activityTimelineEntity(eventId = "other-tenant", tenantId = "tenant-b"),
                activityTimelineEntity(eventId = "other-actor", actorId = "user-999"),
                activityTimelineEntity(eventId = "other-source", source = "mobile-client"),
                activityTimelineEntity(eventId = "other-type", eventType = "like_created"),
                activityTimelineEntity(eventId = "other-object", objectId = "post-999"),
                activityTimelineEntity(eventId = "other-target", targetId = "feed-999"),
                activityTimelineEntity(eventId = "too-early", occurredAt = Instant.parse("2026-06-13T09:59:59Z")),
                activityTimelineEntity(eventId = "too-late", occurredAt = Instant.parse("2026-06-13T11:00:01Z")),
            ),
        )

        val results = repository.findAll(
            ActivityTimelineSpecifications.from(
                ActivityTimelineQuery(
                    tenantId = "tenant-a",
                    actorId = "actor-123",
                    source = "activity-generator",
                    eventType = "post_created",
                    objectId = "post-456",
                    targetId = "feed-789",
                    occurredFrom = Instant.parse("2026-06-13T10:00:00Z"),
                    occurredTo = Instant.parse("2026-06-13T11:00:00Z"),
                    limit = 10,
                ),
            ),
            PageRequest.of(0, 10),
        )

        assertEquals(listOf("match"), results.content.map { it.eventId })
    }

    @Test
    fun `activity timeline query returns stable newest first limited results`() {
        repository.saveAllAndFlush(
            listOf(
                activityTimelineEntity(eventId = "old", occurredAt = Instant.parse("2026-06-13T10:00:00Z")),
                activityTimelineEntity(eventId = "newer-a", occurredAt = Instant.parse("2026-06-13T11:00:00Z")),
                activityTimelineEntity(eventId = "newer-b", occurredAt = Instant.parse("2026-06-13T11:00:00Z")),
            ),
        )

        val results = repository.findAll(
            ActivityTimelineSpecifications.from(ActivityTimelineQuery(tenantId = "tenant-a", limit = 2)),
            PageRequest.of(
                0,
                2,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")),
            ),
        )

        assertEquals(2, results.content.size)
        assertEquals(listOf("newer-b", "newer-a"), results.content.map { it.eventId })
    }

    private fun activityTimelineEntity(
        eventId: String,
        tenantId: String = "tenant-a",
        actorId: String = "actor-123",
        source: String = "activity-generator",
        eventType: String = "post_created",
        objectId: String = "post-456",
        targetId: String? = "feed-789",
        occurredAt: Instant = Instant.parse("2026-06-13T10:15:30Z"),
    ) = ActivityTimelineEntity(
        eventId = eventId,
        tenantId = tenantId,
        actorId = actorId,
        source = source,
        eventType = eventType,
        objectId = objectId,
        targetId = targetId,
        summary = "actor-123 created post-456",
        occurredAt = occurredAt,
        projectedAt = Instant.parse("2026-06-13T10:15:31Z"),
    )
}
