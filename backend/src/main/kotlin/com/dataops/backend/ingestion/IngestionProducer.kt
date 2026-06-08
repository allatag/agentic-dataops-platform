package com.dataops.backend.ingestion

import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class IngestionProducer(
    private val kafkaTemplate: KafkaTemplate<String, RawEvent>,
    @Value("\${app.kafka.topic.raw-events}") private val topic: String,
) {

    fun publish(event: RawEvent) {
        kafkaTemplate.send(topic, event.tenantId, event)
    }
}
