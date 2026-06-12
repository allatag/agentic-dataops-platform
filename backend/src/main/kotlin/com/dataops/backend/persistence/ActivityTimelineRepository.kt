package com.dataops.backend.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ActivityTimelineRepository : JpaRepository<ActivityTimelineEntity, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): ActivityTimelineEntity?
}
