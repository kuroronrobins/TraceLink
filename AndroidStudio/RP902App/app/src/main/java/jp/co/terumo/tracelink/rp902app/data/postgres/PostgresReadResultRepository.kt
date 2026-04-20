package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGateway
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository

/**
 * Read result repository の PostgreSQL 実装入口。
 */
class PostgresReadResultRepository(
    private val postgresGateway: PostgresGateway,
) : ReadResultRepository {
    override suspend fun register(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult =
        postgresGateway.registerReadResults(bundle)
}
