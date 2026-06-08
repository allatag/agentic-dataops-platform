package com.dataops.backend.ingestion

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(IngestionController::class)
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
    fun `missing required field returns 400 Bad Request`() {
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
    }
}
