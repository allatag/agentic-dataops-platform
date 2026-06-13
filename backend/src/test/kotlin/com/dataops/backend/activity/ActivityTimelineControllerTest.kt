package com.dataops.backend.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(ActivityTimelineController::class)
class ActivityTimelineControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: ActivityTimelineService

    @Test
    fun `query activity timeline passes filters and defaults to bounded limit`() {
        whenever(service.query(org.mockito.kotlin.any())).thenReturn(
            ActivityTimelineResponse(limit = 50, count = 0, items = emptyList()),
        )

        mockMvc.get("/api/activity") {
            param("tenantId", "tenant-a")
            param("actorId", "user-123")
            param("source", "activity-generator")
            param("eventType", "post_created")
            param("objectId", "post-456")
            param("targetId", "feed-789")
            param("occurredFrom", "2026-06-13T10:00:00Z")
            param("occurredTo", "2026-06-13T11:00:00Z")
        }.andExpect {
            status { isOk() }
            jsonPath("$.limit") { value(50) }
            jsonPath("$.count") { value(0) }
        }

        val queryCaptor = argumentCaptor<ActivityTimelineQuery>()
        verify(service).query(queryCaptor.capture())
        assertEquals("tenant-a", queryCaptor.firstValue.tenantId)
        assertEquals("user-123", queryCaptor.firstValue.actorId)
        assertEquals("activity-generator", queryCaptor.firstValue.source)
        assertEquals("post_created", queryCaptor.firstValue.eventType)
        assertEquals("post-456", queryCaptor.firstValue.objectId)
        assertEquals("feed-789", queryCaptor.firstValue.targetId)
        assertEquals(Instant.parse("2026-06-13T10:00:00Z"), queryCaptor.firstValue.occurredFrom)
        assertEquals(Instant.parse("2026-06-13T11:00:00Z"), queryCaptor.firstValue.occurredTo)
        assertEquals(50, queryCaptor.firstValue.limit)
    }

    @Test
    fun `query activity timeline returns derived fields only`() {
        whenever(service.query(org.mockito.kotlin.any())).thenReturn(
            ActivityTimelineResponse(
                limit = 1,
                count = 1,
                items = listOf(
                    ActivityTimelineItemResponse(
                        eventId = "event-1",
                        tenantId = "tenant-a",
                        actorId = "user-123",
                        source = "activity-generator",
                        eventType = "post_created",
                        objectId = "post-456",
                        targetId = null,
                        summary = "user-123 post_created post-456",
                        occurredAt = Instant.parse("2026-06-13T10:15:30Z"),
                        projectedAt = Instant.parse("2026-06-13T10:15:31Z"),
                    ),
                ),
            ),
        )

        mockMvc.get("/api/activity") {
            param("tenantId", "tenant-a")
            param("limit", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].eventId") { value("event-1") }
            jsonPath("$.items[0].summary") { value("user-123 post_created post-456") }
            jsonPath("$.items[0].payload") { doesNotExist() }
        }
    }

    @Test
    fun `query activity timeline rejects missing tenant id`() {
        mockMvc.get("/api/activity")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `query activity timeline rejects unbounded limit`() {
        mockMvc.get("/api/activity") {
            param("tenantId", "tenant-a")
            param("limit", "101")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `query activity timeline rejects inverted time range`() {
        mockMvc.get("/api/activity") {
            param("tenantId", "tenant-a")
            param("occurredFrom", "2026-06-13T11:00:00Z")
            param("occurredTo", "2026-06-13T10:00:00Z")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
