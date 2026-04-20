package jp.co.terumo.tracelink.rp902app.data.postgres

import java.sql.SQLException
import java.sql.SQLTimeoutException
import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGatewayFailureKind
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PostgresErrorMapperTest {
    @Test
    fun toGatewayException_mapsTimeoutAsRetryable() {
        val exception = PostgresErrorMapper.toGatewayException(
            operation = "fetch work context",
            throwable = SQLTimeoutException("timeout"),
        )

        assertEquals(PostgresGatewayFailureKind.Retryable, exception.kind)
    }

    @Test
    fun toGatewayException_mapsAuthSqlStateAsConfiguration() {
        val exception = PostgresErrorMapper.toGatewayException(
            operation = "fetch work context",
            throwable = SQLException("bad password", "28P01"),
        )

        assertEquals(PostgresGatewayFailureKind.Configuration, exception.kind)
    }

    @Test
    fun toGatewayException_mapsUndefinedFunctionAsContract() {
        val exception = PostgresErrorMapper.toGatewayException(
            operation = "fetch rule bundle",
            throwable = SQLException("function missing", "42883"),
        )

        assertEquals(PostgresGatewayFailureKind.Contract, exception.kind)
    }

    @Test
    fun toRegistrationFailureKind_keepsFailureCategory() {
        assertEquals(
            RegistrationFailureKind.Configuration,
            PostgresErrorMapper.toRegistrationFailureKind(PostgresGatewayFailureKind.Configuration),
        )
    }

    @Test
    fun toGatewayException_mapsUnclassifiedSqlStateAsUnknown() {
        val exception = PostgresErrorMapper.toGatewayException(
            operation = "register read results",
            throwable = SQLException("unexpected", "HY000"),
        )

        assertEquals(PostgresGatewayFailureKind.Unknown, exception.kind)
    }
}
