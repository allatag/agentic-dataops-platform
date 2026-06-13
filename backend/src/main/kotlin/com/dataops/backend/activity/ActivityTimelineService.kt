package com.dataops.backend.activity

import com.dataops.backend.persistence.ActivityTimelineEntity
import com.dataops.backend.persistence.ActivityTimelineRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

@Service
class ActivityTimelineService(
    private val repository: ActivityTimelineRepository,
) {

    fun query(query: ActivityTimelineQuery): ActivityTimelineResponse {
        val page = PageRequest.of(
            0,
            query.limit,
            Sort.by(
                Sort.Order.desc("occurredAt"),
                Sort.Order.desc("id"),
            ),
        )
        val items = repository.findAll(ActivityTimelineSpecifications.from(query), page)
            .content
            .map { it.toResponse() }

        return ActivityTimelineResponse(
            limit = query.limit,
            count = items.size,
            items = items,
        )
    }

    private fun ActivityTimelineEntity.toResponse() = ActivityTimelineItemResponse(
        eventId = eventId,
        tenantId = tenantId,
        actorId = actorId,
        source = source,
        eventType = eventType,
        objectId = objectId,
        targetId = targetId,
        summary = summary,
        occurredAt = occurredAt,
        projectedAt = projectedAt,
    )
}

object ActivityTimelineSpecifications {
    fun from(query: ActivityTimelineQuery): Specification<ActivityTimelineEntity> {
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>(
                criteriaBuilder.equal(root.get<String>("tenantId"), query.tenantId),
            )

            query.actorId?.let { predicates += criteriaBuilder.equal(root.get<String>("actorId"), it) }
            query.source?.let { predicates += criteriaBuilder.equal(root.get<String>("source"), it) }
            query.eventType?.let { predicates += criteriaBuilder.equal(root.get<String>("eventType"), it) }
            query.objectId?.let { predicates += criteriaBuilder.equal(root.get<String>("objectId"), it) }
            query.targetId?.let { predicates += criteriaBuilder.equal(root.get<String>("targetId"), it) }
            query.occurredFrom?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), it)
            }
            query.occurredTo?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), it)
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }
}
