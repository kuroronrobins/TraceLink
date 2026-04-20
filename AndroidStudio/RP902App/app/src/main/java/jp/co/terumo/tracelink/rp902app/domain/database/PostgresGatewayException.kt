package jp.co.terumo.tracelink.rp902app.domain.database

/**
 * App-owned failure emitted by the PostgreSQL boundary.
 */
class PostgresGatewayException(
    val kind: PostgresGatewayFailureKind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class PostgresGatewayFailureKind {
    Retryable,
    Configuration,
    Contract,
}
