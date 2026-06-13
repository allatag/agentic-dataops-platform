package com.dataops.backend.activity

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [ActivityTimelineController::class])
class ActivityTimelineExceptionHandler {

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(): Map<String, String> {
        return mapOf("error" to "Invalid activity timeline query parameter")
    }
}
