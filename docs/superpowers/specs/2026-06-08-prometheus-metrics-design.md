# Design: Expose Backend Prometheus Metrics via Spring Actuator

**Issue:** #24  
**Date:** 2026-06-08  
**Status:** Approved

## Goal

Make the ingestion backend scrapeable by Prometheus by exposing `/actuator/prometheus` with standard JVM and HTTP metrics.

## Scope

- Add Micrometer Prometheus registry dependency
- Expose `/actuator/prometheus` endpoint
- Keep existing `/actuator/health` and `/actuator/info` behavior unchanged
- Add an automated integration test for the endpoint

## Out of Scope

- Grafana dashboards
- Prometheus container/scrape config
- Custom business metrics
- Load testing scripts
- MDC/log correlation

---

## Changes

### 1. Dependency (`build.gradle.kts`)

Add:

```kotlin
runtimeOnly("io.micrometer:micrometer-registry-prometheus")
```

`runtimeOnly` is correct — Micrometer's auto-configuration wires everything at startup; no compile-time imports are needed. Spring Boot's dependency management provides the version.

### 2. Configuration (`src/main/resources/application.yml`)

Extend the exposed endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

No changes to `src/test/resources/application.yml`.

### 3. Test (`src/test/kotlin/com/dataops/backend/ActuatorPrometheusTest.kt`)

New `@SpringBootTest` integration test with `RANDOM_PORT`:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorPrometheusTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `prometheus endpoint returns metrics in text format`() {
        val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType.toString()).contains("text/plain")
        assertThat(response.body).contains("jvm_memory_used_bytes")
    }
}
```

- Uses `TestRestTemplate` (auto-configured with `RANDOM_PORT`) — hits the real endpoint, no mocking
- Asserts HTTP 200, `text/plain` content-type, and presence of at least one JVM metric
- No external infrastructure needed — existing H2 + disabled Kafka listener test config handles the full context load

---

## Acceptance Criteria

- `./gradlew test` passes
- `GET http://localhost:8080/actuator/prometheus` returns Prometheus text format after `bootRun`
- Response includes `jvm_memory_used_bytes` and HTTP server request metrics after a request to `POST /api/events`
