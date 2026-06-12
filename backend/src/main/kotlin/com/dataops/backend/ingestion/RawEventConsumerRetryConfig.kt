package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcContext
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.util.backoff.FixedBackOff
import java.time.Duration

@ConfigurationProperties(prefix = "app.kafka.consumer.retry")
data class RawEventConsumerRetryProperties(
    val maxAttempts: Long = 3,
    val backoff: Duration = Duration.ofSeconds(1),
) {
    init {
        require(maxAttempts >= 1) { "app.kafka.consumer.retry.max-attempts must be at least 1" }
        require(backoff.toMillis() >= 1) {
            "app.kafka.consumer.retry.backoff must be at least 1ms"
        }
    }

    val retryAttempts: Long
        get() = maxAttempts - 1

    val backoffMs: Long
        get() = backoff.toMillis()
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RawEventConsumerRetryProperties::class)
class RawEventConsumerRetryConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun rawEventConsumerErrorHandler(
        properties: RawEventConsumerRetryProperties,
        rawEventDltRecoverer: ConsumerRecordRecoverer,
    ): DefaultErrorHandler {
        return DefaultErrorHandler(
            rawEventDltRecoverer,
            FixedBackOff(properties.backoffMs, properties.retryAttempts),
        ).apply {
            addNotRetryableExceptions(
                NonRetryableRawEventException::class.java,
            )
            setRetryListeners(
                RetryListener { record, ex, deliveryAttempt ->
                    logWithEventContext(record) {
                        val classification = RawEventErrorClassifier.classify(ex)
                        if (deliveryAttempt < properties.maxAttempts) {
                            log.warn(
                                "Raw event consumer failed classification={}; retrying delivery topic={} partition={} " +
                                    "offset={} failedAttempt={} maxAttempts={} backoffMs={}",
                                classification,
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                deliveryAttempt,
                                properties.maxAttempts,
                                properties.backoffMs,
                                ex,
                            )
                        } else {
                            log.warn(
                                "Raw event consumer failed classification={} on final configured attempt topic={} partition={} " +
                                    "offset={} failedAttempt={} maxAttempts={}",
                                classification,
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                deliveryAttempt,
                                properties.maxAttempts,
                                ex,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun logWithEventContext(record: ConsumerRecord<*, *>, block: () -> Unit) {
        val value = record.value()
        if (value is RawEvent) {
            MdcContext.withEvent(value, block)
        } else {
            block()
        }
    }
}
