package com.dataops.backend.activity

import com.dataops.backend.persistence.ActivityTimelineEntity
import com.dataops.backend.persistence.ActivityTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

class ActivityTimelineServiceTest {

    private val repository = mock<ActivityTimelineRepository>()
    private val service = ActivityTimelineService(repository)

    @Test
    fun `query maps timeline entities to response DTOs`() {
        whenever(repository.findAll(any<Specification<ActivityTimelineEntity>>(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(activityTimelineEntity())))

        val response = service.query(ActivityTimelineQuery(tenantId = "tenant-a", limit = 25))

        assertEquals(25, response.limit)
        assertEquals(1, response.count)
        assertEquals("event-1", response.items.single().eventId)
        assertEquals("user-123", response.items.single().actorId)
        assertEquals("post-456", response.items.single().objectId)
    }

    @Test
    fun `query requests stable newest first ordering and bounded page size`() {
        whenever(repository.findAll(any<Specification<ActivityTimelineEntity>>(), any<Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        service.query(ActivityTimelineQuery(tenantId = "tenant-a", limit = 10))

        val pageableCaptor = argumentCaptor<Pageable>()
        verify(repository).findAll(any<Specification<ActivityTimelineEntity>>(), pageableCaptor.capture())

        val pageable = pageableCaptor.firstValue
        assertEquals(0, pageable.pageNumber)
        assertEquals(10, pageable.pageSize)
        assertEquals(Sort.Direction.DESC, pageable.sort.getOrderFor("occurredAt")!!.direction)
        assertEquals(Sort.Direction.DESC, pageable.sort.getOrderFor("id")!!.direction)
    }

    private fun activityTimelineEntity() = ActivityTimelineEntity(
        id = 42,
        eventId = "event-1",
        tenantId = "tenant-a",
        actorId = "user-123",
        source = "activity-generator",
        eventType = "post_created",
        objectId = "post-456",
        targetId = null,
        summary = "user-123 created post-456",
        occurredAt = Instant.parse("2026-06-13T10:15:30Z"),
        projectedAt = Instant.parse("2026-06-13T10:15:31Z"),
    )
}
