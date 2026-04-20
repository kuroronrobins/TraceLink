package jp.co.terumo.tracelink.rp902app.data.postgres

import java.util.Properties

data class PostgresConnectionSettings(
    val host: String,
    val port: Int = 5432,
    val databaseName: String,
    val username: String,
    val password: String,
    val sslMode: PostgresSslMode = PostgresSslMode.Require,
    val connectTimeoutMillis: Int = 5_000,
    val socketTimeoutMillis: Int = 15_000,
) {
    init {
        require(host.isNotBlank()) { "PostgreSQL host must not be blank." }
        require(port in 1..65_535) { "PostgreSQL port must be between 1 and 65535." }
        require(databaseName.isNotBlank()) { "PostgreSQL database name must not be blank." }
        require(username.isNotBlank()) { "PostgreSQL username must not be blank." }
        require(connectTimeoutMillis > 0) { "PostgreSQL connect timeout must be positive." }
        require(socketTimeoutMillis > 0) { "PostgreSQL socket timeout must be positive." }
    }

    fun jdbcUrl(): String = "jdbc:postgresql://$host:$port/$databaseName"

    fun jdbcProperties(): Properties = Properties().apply {
        setProperty("user", username)
        setProperty("password", password)
        setProperty("sslmode", sslMode.jdbcValue)
        setProperty("connectTimeout", connectTimeoutMillis.toSecondsForJdbc())
        setProperty("socketTimeout", socketTimeoutMillis.toSecondsForJdbc())
        setProperty("ApplicationName", "TraceLink-RP902-Android")
    }

    internal fun queryTimeoutSeconds(): Int = socketTimeoutMillis.toSecondsForJdbc().toInt()

    private fun Int.toSecondsForJdbc(): String = ((this + 999) / 1000).toString()
}

enum class PostgresSslMode(val jdbcValue: String) {
    Disable("disable"),
    Prefer("prefer"),
    Require("require"),
    VerifyCa("verify-ca"),
    VerifyFull("verify-full"),
}
