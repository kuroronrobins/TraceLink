package jp.co.terumo.tracelink.rp902app.data.rule

import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleRepository
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * PostgreSQL 未接続期間に使う固定 rule bundle。
 */
class FakeRuleRepository(
    private val ruleVersion: String = "fake-rule-v1",
    private val targetEquipmentTypes: Set<String> = setOf("traceable-equipment"),
) : RuleRepository {
    override suspend fun fetchRuleBundle(workContext: WorkContext): RuleBundle =
        RuleBundle(
            ruleVersion = ruleVersion,
            reportId = workContext.reportId,
            effectiveAtEpochMillis = workContext.startedAtEpochMillis,
            targetEquipmentTypes = targetEquipmentTypes,
        )
}
