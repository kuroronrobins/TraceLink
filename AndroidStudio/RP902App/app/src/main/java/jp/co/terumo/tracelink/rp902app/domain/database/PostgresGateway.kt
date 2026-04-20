package jp.co.terumo.tracelink.rp902app.domain.database

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * PostgreSQL 直接アクセスの低レベル境界。
 *
 * Repository 実装はこの gateway を使う想定だが、UI / ViewModel / domain service は依存しない。
 * 実装時は table 直叩きではなく、Android 用 View / Function / 制約を通す。
 */
interface PostgresGateway {
    suspend fun fetchWorkContext(): WorkContext
    suspend fun fetchRuleBundle(workContext: WorkContext): RuleBundle
    suspend fun fetchEquipmentSnapshot(workContext: WorkContext): EquipmentSnapshot
    suspend fun registerReadResults(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult
}
