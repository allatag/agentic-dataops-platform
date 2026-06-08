package com.dataops.backend.ingestion

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(IngestionController::class)
class IngestionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

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
