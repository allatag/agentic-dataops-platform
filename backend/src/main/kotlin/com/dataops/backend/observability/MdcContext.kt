package com.dataops.backend.observability

import com.dataops.backend.ingestion.RawEvent
import org.slf4j.MDC

object MdcKeys {
    const val CORRELATION_ID = "correlationId"
    const val TENANT_ID = "tenantId"
    const val EVENT_ID = "eventId"
    const val SOURCE = "source"
    const val EVENT_TYPE = "eventType"
}

object MdcContext {
    private val eventKeys = listOf(
        MdcKeys.CORRELATION_ID,
        MdcKeys.TENANT_ID,
        MdcKeys.EVENT_ID,
        MdcKeys.SOURCE,
        MdcKeys.EVENT_TYPE,
    )

    fun currentCorrelationId(): String? = MDC.get(MdcKeys.CORRELATION_ID)

    fun <T> withEvent(event: RawEvent, block: () -> T): T {
        val previousValues = eventKeys.associateWith { key -> MDC.get(key) }

        MDC.put(MdcKeys.CORRELATION_ID, event.correlationId)
        MDC.put(MdcKeys.TENANT_ID, event.tenantId)
        MDC.put(MdcKeys.EVENT_ID, event.eventId)
        MDC.put(MdcKeys.SOURCE, event.source)
        MDC.put(MdcKeys.EVENT_TYPE, event.eventType)

        return try {
            block()
        } finally {
            previousValues.forEach { (key, value) ->
                if (value == null) {
                    MDC.remove(key)
                } else {
                    MDC.put(key, value)
                }
            }
        }
    }
}
