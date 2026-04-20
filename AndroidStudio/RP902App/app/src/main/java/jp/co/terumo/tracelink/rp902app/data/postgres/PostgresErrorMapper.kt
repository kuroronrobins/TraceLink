package jp.co.terumo.tracelink.rp902app.data.postgres

import java.net.SocketTimeoutException
import java.sql.SQLException
import java.sql.SQLTimeoutException
import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGatewayException
import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGatewayFailureKind
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind

internal object PostgresErrorMapper {
    fun toGatewayException(
        operation: String,
        throwable: Throwable,
    ): PostgresGatewayException {
        if (throwable is PostgresGatewayException) return throwable

        val kind = classify(throwable)
        val message = "PostgreSQL $operation failed: ${throwable.message.orEmpty()}"
        return PostgresGatewayException(
            kind = kind,
            message = message,
            cause = throwable,
        )
    }

    fun toRegistrationFailureKind(kind: PostgresGatewayFailureKind): RegistrationFailureKind =
        when (kind) {
            PostgresGatewayFailureKind.Retryable -> RegistrationFailureKind.Retryable
            PostgresGatewayFailureKind.Configuration -> RegistrationFailureKind.Configuration
            PostgresGatewayFailureKind.Contract -> RegistrationFailureKind.Contract
            PostgresGatewayFailureKind.Unknown -> RegistrationFailureKind.Unknown
        }

    private fun classify(throwable: Throwable): PostgresGatewayFailureKind =
        when (throwable) {
            is SQLTimeoutException,
            is SocketTimeoutException -> PostgresGatewayFailureKind.Retryable

            is SQLException -> classifySqlState(throwable.sqlState)
            is ClassNotFoundException -> PostgresGatewayFailureKind.Configuration
            is IllegalArgumentException,
            is IllegalStateException -> PostgresGatewayFailureKind.Contract

            else -> PostgresGatewayFailureKind.Unknown
        }

    private fun classifySqlState(sqlState: String?): PostgresGatewayFailureKind {
        if (sqlState.isNullOrBlank()) return PostgresGatewayFailureKind.Unknown

        return when {
            sqlState.startsWith("08") -> PostgresGatewayFailureKind.Retryable
            sqlState.startsWith("28") -> PostgresGatewayFailureKind.Configuration
            sqlState in contractSqlStates -> PostgresGatewayFailureKind.Contract
            sqlState.startsWith("22") -> PostgresGatewayFailureKind.Contract
            sqlState.startsWith("42") -> PostgresGatewayFailureKind.Contract
            else -> PostgresGatewayFailureKind.Unknown
        }
    }

    private val contractSqlStates = setOf(
        "0A000",
        "21000",
        "42804",
        "42883",
        "42P01",
        "42703",
    )
}
