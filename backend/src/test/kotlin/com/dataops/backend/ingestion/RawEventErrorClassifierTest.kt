package com.dataops.backend.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.TransientDataAccessResourceException

class RawEventErrorClassifierTest {

    @Test
    fun `consumer validation errors are non retryable`() {
        val classification = RawEventErrorClassifier.classify(
            NonRetryableRawEventException("Unsupported raw event schemaVersion=2"),
        )

        assertEquals(RawEventErrorClassification.NON_RETRYABLE, classification)
    }

    @Test
    fun `transient persistence errors are retryable`() {
        val classification = RawEventErrorClassifier.classify(
            TransientDataAccessResourceException("temporary database connectivity failure"),
        )

        assertEquals(RawEventErrorClassification.RETRYABLE, classification)
    }
}
