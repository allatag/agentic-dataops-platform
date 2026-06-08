package com.dataops.backend.ingestion

import com.dataops.backend.observability.CorrelationIdFilter
import com.dataops.backend.observability.MdcKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(IngestionController::class)
@ContextConfiguration(classes = [IngestionController::class, CorrelationIdFilter::class])
class IngestionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var producer: IngestionProducer

    @Test
    fun `valid request returns 202 Accepted`() {
        mockMvc.post("/api/events") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "tenantId": "tenant-a",
                    "source": "payment-service",
                    "eventType": "LATENCY_SPIKE",
                    "severity": "HIGH",
                    "message": "P95 latency increased above threshold"
                }
            """.trimIndent()
        }.andExpect {
            status { isAccepted() }
        }

        verify(producer).publish(any())
    }

    @Test
    fun `valid request uses incoming correlation id and clears MDC after request`() {
        val eventCaptor = argumentCaptor<RawEvent>()

        doAnswer { invocation ->
            val event = invocation.arguments[0] as RawEvent
            assertEquals("request-123", MDC.get(MdcKeys.CORRELATION_ID))
            assertEquals(event.eventId, MDC.get(MdcKeys.EVENT_ID))
            assertEquals("tenant-a", MDC.get(MdcKeys.TENANT_ID))
            assertEquals("payment-service", MDC.get(MdcKeys.SOURCE))
            assertEquals("LATENCY_SPIKE", MDC.get(MdcKeys.EVENT_TYPE))
            null
        }.whenever(producer).publish(any())

        mockMvc.post("/api/events") {
            header("X-Correlation-Id", "request-123")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "tenantId": "tenant-a",
                    "source": "payment-service",
                    "eventType": "LATENCY_SPIKE",
                    "severity": "HIGH",
                    "message": "P95 latency increased above threshold"
                }
            """.trimIndent()
        }.andExpect {
            status { isAccepted() }
        }

        verify(producer).publish(eventCaptor.capture())
        assertEquals("request-123", eventCaptor.firstValue.correlationId)
        assertNull(MDC.get(MdcKeys.CORRELATION_ID))
        assertNull(MDC.get(MdcKeys.EVENT_ID))
        assertNull(MDC.get(MdcKeys.TENANT_ID))
        assertNull(MDC.get(MdcKeys.SOURCE))
        assertNull(MDC.get(MdcKeys.EVENT_TYPE))
    }

    @Test
    fun `missing required field returns 400 Bad Request and does not publish`() {
        mockMvc.post("/api/events") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "tenantId": "tenant-a",
                    "source": "payment-service"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(producer)
    }
}
