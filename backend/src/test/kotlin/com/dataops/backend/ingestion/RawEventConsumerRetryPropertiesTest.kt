package com.dataops.backend.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class RawEventConsumerRetryPropertiesTest {

    @Test
    fun `retry backoff must be at least one millisecond`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            RawEventConsumerRetryProperties(backoff = Duration.ofNanos(500_000))
        }

        assertEquals("app.kafka.consumer.retry.backoff must be at least 1ms", exception.message)
    }

    @Test
    fun `retry attempts excludes the first delivery`() {
        val properties = RawEventConsumerRetryProperties(maxAttempts = 3, backoff = Duration.ofMillis(1))

        assertEquals(2, properties.retryAttempts)
        assertEquals(1, properties.backoffMs)
    }
}
