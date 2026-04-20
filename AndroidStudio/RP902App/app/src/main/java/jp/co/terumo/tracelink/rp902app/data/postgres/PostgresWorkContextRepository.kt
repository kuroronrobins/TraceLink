package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGateway
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContextRepository

/**
 * Work context repository の PostgreSQL 実装入口。
 *
 * SQL や View / Function 名は `PostgresGateway` 実装へ閉じ込める。
 */
class PostgresWorkContextRepository(
    private val postgresGateway: PostgresGateway,
) : WorkContextRepository {
    override suspend fun resolveCurrentWorkContext(): WorkContext =
        postgresGateway.fetchWorkContext()
}
