package com.dataops.backend.ingestion

import com.fasterxml.jackson.core.JsonProcessingException
import org.springframework.kafka.support.serializer.DeserializationException

enum class RawEventErrorClassification {
    RETRYABLE,
    NON_RETRYABLE,
}

class NonRetryableRawEventException(message: String) : RuntimeException(message)

object RawEventErrorClassifier {
    private val nonRetryableExceptions = setOf(
        NonRetryableRawEventException::class.java,
        DeserializationException::class.java,
        JsonProcessingException::class.java,
    )

    fun classify(exception: Throwable): RawEventErrorClassification {
        val causeChain = generateSequence(exception) { it.cause }
        return if (causeChain.any { cause -> nonRetryableExceptions.any { it.isInstance(cause) } }) {
            RawEventErrorClassification.NON_RETRYABLE
        } else {
            RawEventErrorClassification.RETRYABLE
        }
    }
}
