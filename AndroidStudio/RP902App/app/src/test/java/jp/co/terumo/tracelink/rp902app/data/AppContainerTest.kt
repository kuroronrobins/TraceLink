package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresConnectionSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppContainerTest {
    @Test
    fun fakeMode_createsInventoryRepositoryWithoutPostgresSettings() {
        val container = AppContainer(dataAccessMode = DataAccessMode.Fake)

        assertNotNull(container.inventoryRepository())
    }

    @Test
    fun postgresMode_requiresPostgresConnectionSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            AppContainer(dataAccessMode = DataAccessMode.Postgres)
        }
    }

    @Test
    fun postgresMode_canCreateRepositoryWhenSettingsAreProvided() {
        val container = AppContainer(
            dataAccessMode = DataAccessMode.Postgres,
            postgresConnectionSettings = PostgresConnectionSettings(
                host = "db.example.local",
                databaseName = "tracelink",
                username = "app_user",
                password = "secret",
            ),
        )

        assertNotNull(container.inventoryRepository())
    }
}
