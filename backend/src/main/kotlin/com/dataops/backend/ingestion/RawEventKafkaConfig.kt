package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

@Configuration
class RawEventKafkaConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun rawEventsTopic(
        @Value("\${app.kafka.topic.raw-events}") topic: String,
    ): NewTopic = TopicBuilder.name(topic)
        .partitions(1)
        .replicas(1)
        .build()

    @Bean
    fun rawEventsDeadLetterTopic(
        @Value("\${app.kafka.topic.raw-events-dlt}") topic: String,
    ): NewTopic = TopicBuilder.name(topic)
        .partitions(1)
        .replicas(1)
        .build()

    @Bean
    fun rawEventConsumerErrorHandler(
        rawEventDltRecoverer: ConsumerRecordRecoverer,
        @Value("\${app.kafka.consumer.retry.backoff-ms}") retryBackoffMs: Long,
        @Value("\${app.kafka.consumer.retry.max-retries}") maxRetries: Long,
    ): CommonErrorHandler =
        DefaultErrorHandler(rawEventDltRecoverer, FixedBackOff(retryBackoffMs, maxRetries))

    @Bean
    fun rawEventDltRecoverer(
        kafkaTemplate: KafkaTemplate<String, RawEvent>,
        @Value("\${app.kafka.topic.raw-events-dlt}") dltTopic: String,
    ): ConsumerRecordRecoverer = ConsumerRecordRecoverer { record, exception ->
        val event = record.value() as? RawEvent
            ?: throw IllegalStateException(
                "Cannot send failed record from topic ${record.topic()} offset ${record.offset()} to DLT because it is not a RawEvent",
                exception,
            )

        MdcContext.withEvent(event) {
            log.error(
                "Sending failed raw event to DLT topic {} after retry exhaustion from topic {} partition {} offset {}",
                dltTopic,
                record.topic(),
                record.partition(),
                record.offset(),
                exception,
            )

            runCatching {
                kafkaTemplate.send(dltTopic, record.keyAsString() ?: event.tenantId, event)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join()
            }.getOrElse { throwable ->
                val cause = (throwable as? CompletionException)?.cause ?: throwable
                throw IllegalStateException("Failed to publish event ${event.eventId} to DLT topic $dltTopic", cause)
            }

            log.warn("Sent failed raw event to DLT topic {}", dltTopic)
        }
    }

    private fun ConsumerRecord<*, *>.keyAsString(): String? = key()?.toString()
}
