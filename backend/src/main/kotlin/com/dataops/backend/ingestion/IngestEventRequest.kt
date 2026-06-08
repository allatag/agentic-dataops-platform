package com.dataops.backend.ingestion

import jakarta.validation.constraints.NotBlank

data class IngestEventRequest(
    @field:NotBlank val tenantId: String = "",
    @field:NotBlank val source: String = "",
    @field:NotBlank val eventType: String = "",
    @field:NotBlank val severity: String = "",
    @field:NotBlank val message: String = "",
)
