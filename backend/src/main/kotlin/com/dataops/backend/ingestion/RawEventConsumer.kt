package com.dataops.backend.ingestion

import com.dataops.backend.observability.MdcContext
import com.dataops.backend.persistence.RawEventEntity
import com.dataops.backend.persistence.RawEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

private const val EVENT_ID_CONSTRAINT = "uq_raw_event_event_id"

@Service
class RawEventConsumer(
    private val repository: RawEventRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${app.kafka.topic.raw-events}"], groupId = "\${spring.kafka.consumer.group-id}")
    fun consume(event: RawEvent) {
        MdcContext.withEvent(event) {
            log.info("Received event for tenant")

            val entity = RawEventEntity(
                eventId = event.eventId,
                schemaVersion = event.schemaVersion,
                tenantId = event.tenantId,
                source = event.source,
                eventType = event.eventType,
                severity = event.severity,
                occurredAt = event.occurredAt,
                receivedAt = event.receivedAt,
                payloadJson = objectMapper.writeValueAsString(event.payload),
            )

            try {
                repository.save(entity)
                log.info("Persisted event to raw_event")
            } catch (ex: DataIntegrityViolationException) {
                if (ex.mostSpecificCause.message?.contains(EVENT_ID_CONSTRAINT) == true) {
                    log.warn("Duplicate event - skipping")
                } else {
                    throw ex
                }
            }
        }
    }
}
