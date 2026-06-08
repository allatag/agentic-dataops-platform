package com.dataops.backend.ingestion

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/events")
class IngestionController(private val producer: IngestionProducer) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun ingest(@Valid @RequestBody request: IngestEventRequest) {
        val event = RawEvent(
            tenantId = request.tenantId,
            source = request.source,
            eventType = request.eventType,
            severity = request.severity,
            occurredAt = Instant.now(),
            payload = mapOf("message" to request.message),
        )
        producer.publish(event)
    }
}
