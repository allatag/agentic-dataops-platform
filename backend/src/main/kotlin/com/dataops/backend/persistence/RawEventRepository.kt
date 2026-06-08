package com.dataops.backend.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RawEventRepository : JpaRepository<RawEventEntity, Long>
