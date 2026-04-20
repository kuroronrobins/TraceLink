package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.BuildConfig
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresConnectionSettings
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresSslMode
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationEnvironment

/**
 * 通常起動を fake-first に保ちつつ、debug build だけ PostgreSQL smoke test に切り替える入口。
 */
object AppContainerFactory {
    fun create(): AppContainer {
        val postgresSmokeSettings = DebugPostgresSmokeSettings.fromBuildConfig()
            ?: return AppContainer()

        return AppContainer(
            dataAccessMode = DataAccessMode.Postgres,
            postgresConnectionSettings = postgresSmokeSettings.connectionSettings,
            registrationEnvironment = postgresSmokeSettings.registrationEnvironment,
        )
    }
}

private data class DebugPostgresSmokeSettings(
    val connectionSettings: PostgresConnectionSettings,
    val registrationEnvironment: RegistrationEnvironment,
) {
    companion object {
        fun fromBuildConfig(): DebugPostgresSmokeSettings? {
            if (!BuildConfig.DEBUG || !BuildConfig.POSTGRES_SMOKE_ENABLED) return null

            require(BuildConfig.POSTGRES_SMOKE_PASSWORD.isNotBlank()) {
                "Debug PostgreSQL smoke mode requires -PtracelinkPostgresPassword. " +
                    "Do not put production credentials in source code."
            }

            return DebugPostgresSmokeSettings(
                connectionSettings = PostgresConnectionSettings(
                    host = BuildConfig.POSTGRES_SMOKE_HOST,
                    port = BuildConfig.POSTGRES_SMOKE_PORT,
                    databaseName = BuildConfig.POSTGRES_SMOKE_DATABASE,
                    username = BuildConfig.POSTGRES_SMOKE_USERNAME,
                    password = BuildConfig.POSTGRES_SMOKE_PASSWORD,
                    sslMode = postgresSslModeFromBuildConfig(),
                ),
                registrationEnvironment = RegistrationEnvironment(
                    deviceId = BuildConfig.POSTGRES_SMOKE_DEVICE_ID,
                    readerType = BuildConfig.POSTGRES_SMOKE_READER_TYPE,
                ),
            )
        }

        private fun postgresSslModeFromBuildConfig(): PostgresSslMode =
            requireNotNull(
                PostgresSslMode.entries.firstOrNull { mode ->
                    mode.name.equals(BuildConfig.POSTGRES_SMOKE_SSL_MODE, ignoreCase = true)
                },
            ) {
                "Unknown debug PostgreSQL SSL mode: ${BuildConfig.POSTGRES_SMOKE_SSL_MODE}"
            }
    }
}
