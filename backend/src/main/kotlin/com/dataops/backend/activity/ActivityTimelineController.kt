package com.dataops.backend.activity

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Validated
@RestController
@RequestMapping("/api/activity")
class ActivityTimelineController(
    private val service: ActivityTimelineService,
) {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun query(
        @RequestParam @NotBlank tenantId: String,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) objectId: String?,
        @RequestParam(required = false) targetId: String?,
        @RequestParam(required = false) occurredFrom: Instant?,
        @RequestParam(required = false) occurredTo: Instant?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_ACTIVITY_TIMELINE_LIMIT.toLong()) limit: Int,
    ): ActivityTimelineResponse {
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "occurredFrom must be before or equal to occurredTo")
        }

        return service.query(
            ActivityTimelineQuery(
                tenantId = tenantId,
                actorId = actorId.normalized(),
                source = source.normalized(),
                eventType = eventType.normalized(),
                objectId = objectId.normalized(),
                targetId = targetId.normalized(),
                occurredFrom = occurredFrom,
                occurredTo = occurredTo,
                limit = limit,
            ),
        )
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
