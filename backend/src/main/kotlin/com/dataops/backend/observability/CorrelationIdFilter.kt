package com.dataops.backend.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val CORRELATION_ID_HEADER = "X-Correlation-Id"

@Component
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = request.getHeader(CORRELATION_ID_HEADER)
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 64 && it.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' } }
            ?: UUID.randomUUID().toString()

        MDC.put(MdcKeys.CORRELATION_ID, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID)
        }
    }
}
