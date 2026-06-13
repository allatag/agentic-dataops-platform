package com.dataops.backend.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface ActivityTimelineRepository :
    JpaRepository<ActivityTimelineEntity, Long>,
    JpaSpecificationExecutor<ActivityTimelineEntity> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): ActivityTimelineEntity?
}
