package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

@Service
class IngestionProducer(
    private val kafkaTemplate: KafkaTemplate<String, RawEvent>,
    @Value("\${app.kafka.topic.raw-events}") private val topic: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(event: RawEvent) {
        MdcContext.withEvent(event) {
            log.info("Publishing event to Kafka topic {}", topic)
            runCatching {
                kafkaTemplate.send(topic, event.tenantId, event)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join()
            }.onSuccess {
                log.info("Published event to Kafka topic {}", topic)
            }.getOrElse { throwable ->
                val cause = (throwable as? CompletionException)?.cause ?: throwable
                throw IllegalStateException("Failed to publish event ${event.eventId} to topic $topic", cause)
            }
        }
    }
}
