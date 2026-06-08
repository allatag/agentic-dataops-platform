package com.dataops.backend.ingestion

import com.dataops.backend.persistence.RawEventEntity
import com.dataops.backend.persistence.RawEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class RawEventConsumer(
    private val repository: RawEventRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${app.kafka.topic.raw-events}"], groupId = "\${spring.kafka.consumer.group-id}")
    fun consume(event: RawEvent) {
        log.info("Received event {} for tenant {}", event.eventId, event.tenantId)

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

        repository.save(entity)
        log.info("Persisted event {} to raw_event", event.eventId)
    }
}
