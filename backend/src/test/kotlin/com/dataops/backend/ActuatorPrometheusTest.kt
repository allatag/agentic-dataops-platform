package com.dataops.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.prometheus.metrics.export.enabled=true"]
)
class ActuatorPrometheusTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `prometheus endpoint returns metrics in text format`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType!!.isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue()
        assertThat(response.body).isNotNull().contains("jvm_memory_used_bytes")
    }
}
