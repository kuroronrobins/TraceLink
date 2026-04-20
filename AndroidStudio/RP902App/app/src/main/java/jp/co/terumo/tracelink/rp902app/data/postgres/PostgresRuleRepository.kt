package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGateway
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleRepository
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * Rule repository の PostgreSQL 実装入口。
 */
class PostgresRuleRepository(
    private val postgresGateway: PostgresGateway,
) : RuleRepository {
    override suspend fun fetchRuleBundle(workContext: WorkContext): RuleBundle =
        postgresGateway.fetchRuleBundle(workContext)
}
