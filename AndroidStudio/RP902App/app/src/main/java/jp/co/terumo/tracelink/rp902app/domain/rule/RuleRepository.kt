package jp.co.terumo.tracelink.rp902app.domain.rule

import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * 帳票ルールを端末内判定で使える単位にまとめた snapshot。
 */
data class RuleBundle(
    val ruleVersion: String,
    val reportId: String,
    val effectiveAtEpochMillis: Long,
    val targetEquipmentTypes: Set<String>,
)

/**
 * Android が PostgreSQL から帳票ルールを取得するための契約。
 */
interface RuleRepository {
    suspend fun fetchRuleBundle(workContext: WorkContext): RuleBundle
}
