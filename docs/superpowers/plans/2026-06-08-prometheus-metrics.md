# Prometheus Metrics Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose `/actuator/prometheus` on the ingestion backend so it can be scraped by Prometheus.

**Architecture:** Add the Micrometer Prometheus registry as a runtime dependency, extend the actuator endpoint exposure list in `application.yml`, and cover the endpoint with a `@SpringBootTest` integration test using `TestRestTemplate`.

**Tech Stack:** Spring Boot 3.5, Micrometer, `micrometer-registry-prometheus`, Kotlin, JUnit 5

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `backend/src/test/kotlin/com/dataops/backend/ActuatorPrometheusTest.kt` | Create | Integration test for `/actuator/prometheus` |
| `backend/build.gradle.kts` | Modify | Add `micrometer-registry-prometheus` runtime dependency |
| `backend/src/main/resources/application.yml` | Modify | Expose `prometheus` actuator endpoint |

---

### Task 1: Write the failing integration test

**Files:**
- Create: `backend/src/test/kotlin/com/dataops/backend/ActuatorPrometheusTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.dataops.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

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

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cd backend && ./gradlew test --tests "com.dataops.backend.ActuatorPrometheusTest" 2>&1 | tail -20
```

Expected: test fails — either `404 Not Found` (endpoint not exposed) or a missing bean/dependency error. A green result here means the test is not testing anything useful.

---

### Task 2: Add the Prometheus registry dependency

**Files:**
- Modify: `backend/build.gradle.kts`

- [ ] **Step 1: Add the dependency**

In `build.gradle.kts`, add this line inside the `dependencies { }` block, after the existing `implementation` lines:

```kotlin
runtimeOnly("io.micrometer:micrometer-registry-prometheus")
```

The full `dependencies` block should look like:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

---

### Task 3: Expose the prometheus actuator endpoint

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add prometheus to the exposed endpoints**

Change the `management` block from:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

to:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

---

### Task 4: Verify all tests pass and commit

- [ ] **Step 1: Run the full test suite**

```bash
cd backend && ./gradlew test 2>&1 | tail -30
```

Expected output includes:

```
BUILD SUCCESSFUL
```

All existing tests (`BackendApplicationTests`, `IngestionControllerTest`, `RawEventConsumerTest`, `RawEventTest`) must still pass alongside the new `ActuatorPrometheusTest`.

- [ ] **Step 2: Commit**

```bash
git add backend/build.gradle.kts \
        backend/src/main/resources/application.yml \
        backend/src/test/kotlin/com/dataops/backend/ActuatorPrometheusTest.kt
git commit -m "feat: expose /actuator/prometheus via Micrometer registry (#24)"
```
