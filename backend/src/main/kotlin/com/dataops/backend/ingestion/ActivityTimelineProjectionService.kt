package com.dataops.backend.ingestion

import com.dataops.backend.persistence.ActivityTimelineEntity
import com.dataops.backend.persistence.ActivityTimelineRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant

private const val ACTIVITY_TIMELINE_EVENT_ID_CONSTRAINT = "uq_activity_timeline_event_id"

@Service
class ActivityTimelineProjectionService(
    private val repository: ActivityTimelineRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun project(event: RawEvent) {
        if (event.eventType !in activityEventTypes) {
            return
        }
        if (repository.existsByEventId(event.eventId)) {
            log.warn("Activity timeline projection already exists - skipping")
            return
        }

        val actorId = event.requiredPayloadText("actorId", "actor_id")
        val objectId = event.requiredPayloadText("objectId", "object_id")
        val targetId = event.optionalPayloadText("targetId", "target_id")
        val summary = event.optionalPayloadText("summary")
            ?: buildSummary(actorId = actorId, eventType = event.eventType, objectId = objectId, targetId = targetId)

        val projection = ActivityTimelineEntity(
            eventId = event.eventId,
            tenantId = event.tenantId,
            actorId = actorId,
            source = event.source,
            eventType = event.eventType,
            objectId = objectId,
            targetId = targetId,
            summary = summary,
            occurredAt = event.occurredAt,
            projectedAt = Instant.now(),
        )

        try {
            repository.saveAndFlush(projection)
            log.info("Projected event to activity_timeline")
        } catch (ex: DataIntegrityViolationException) {
            if (ex.mostSpecificCause.message?.contains(ACTIVITY_TIMELINE_EVENT_ID_CONSTRAINT) == true) {
                log.warn("Activity timeline projection already exists - skipping")
            } else {
                throw ex
            }
        }
    }

    private fun RawEvent.requiredPayloadText(vararg keys: String): String {
        return optionalPayloadText(*keys)
            ?: throw NonRetryableRawEventException(
                "Activity event $eventId is missing required payload field ${keys.first()}",
            )
    }

    private fun RawEvent.optionalPayloadText(vararg keys: String): String? {
        return keys.asSequence()
            .mapNotNull { key -> payload[key] }
            .map { value -> value.toString().trim() }
            .firstOrNull { value -> value.isNotBlank() }
    }

    private fun buildSummary(
        actorId: String,
        eventType: String,
        objectId: String,
        targetId: String?,
    ): String {
        return if (targetId == null) {
            "$actorId $eventType $objectId"
        } else {
            "$actorId $eventType $objectId for $targetId"
        }
    }

    private companion object {
        private val activityEventTypes = setOf(
            "post_created",
            "repost_created",
            "follow_created",
            "like_created",
            "timeline_viewed",
            "notification_clicked",
        )
    }
}
