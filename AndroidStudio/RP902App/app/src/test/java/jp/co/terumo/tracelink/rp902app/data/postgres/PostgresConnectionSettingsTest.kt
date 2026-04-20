package jp.co.terumo.tracelink.rp902app.data.postgres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PostgresConnectionSettingsTest {
    @Test
    fun jdbcUrl_usesHostPortAndDatabaseName() {
        val settings = PostgresConnectionSettings(
            host = "db.example.local",
            port = 15432,
            databaseName = "tracelink",
            username = "app_user",
            password = "secret",
        )

        assertEquals("jdbc:postgresql://db.example.local:15432/tracelink", settings.jdbcUrl())
    }

    @Test
    fun jdbcProperties_includeSslAndTimeouts() {
        val settings = PostgresConnectionSettings(
            host = "db.example.local",
            databaseName = "tracelink",
            username = "app_user",
            password = "secret",
            sslMode = PostgresSslMode.VerifyFull,
            connectTimeoutMillis = 1_500,
            socketTimeoutMillis = 2_001,
        )

        val properties = settings.jdbcProperties()

        assertEquals("app_user", properties.getProperty("user"))
        assertEquals("secret", properties.getProperty("password"))
        assertEquals("verify-full", properties.getProperty("sslmode"))
        assertEquals("2", properties.getProperty("connectTimeout"))
        assertEquals("3", properties.getProperty("socketTimeout"))
    }

    @Test
    fun init_rejectsBlankHost() {
        assertThrows(IllegalArgumentException::class.java) {
            PostgresConnectionSettings(
                host = " ",
                databaseName = "tracelink",
                username = "app_user",
                password = "secret",
            )
        }
    }
}
