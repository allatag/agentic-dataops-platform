package com.dataops.backend.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class ActivityTimelineMigrationTest(
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `flyway creates activity timeline schema that jpa can validate`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'activity_timeline'
            ORDER BY ordinal_position
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "id",
                "event_id",
                "tenant_id",
                "actor_id",
                "source",
                "event_type",
                "object_id",
                "target_id",
                "summary",
                "occurred_at",
                "projected_at",
            ),
            columns.map { it["column_name"] },
        )
        assertEquals("text", columns.first { it["column_name"] == "event_id" }["data_type"])
        assertEquals("timestamp with time zone", columns.first { it["column_name"] == "occurred_at" }["data_type"])
        assertEquals("NO", columns.first { it["column_name"] == "event_id" }["is_nullable"])
        assertEquals("YES", columns.first { it["column_name"] == "target_id" }["is_nullable"])

        assertTrue(constraintExists("uq_activity_timeline_event_id"))
        assertTrue(indexExists("idx_activity_timeline_tenant_occurred_at"))
        assertTrue(indexExists("idx_activity_timeline_tenant_actor_occurred_at"))
        assertTrue(indexExists("idx_activity_timeline_tenant_event_type_occurred_at"))
        assertTrue(indexExists("idx_activity_timeline_tenant_source_occurred_at"))
    }

    private fun constraintExists(constraintName: String): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'activity_timeline'
                  AND constraint_name = ?
            )
            """.trimIndent(),
            Boolean::class.java,
            constraintName,
        ) ?: false

    private fun indexExists(indexName: String): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'activity_timeline'
                  AND indexname = ?
            )
            """.trimIndent(),
            Boolean::class.java,
            indexName,
        ) ?: false

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("dataops_test")
            withUsername("dataops")
            withPassword("dataops")
        }

        @DynamicPropertySource
        @JvmStatic
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }
}
