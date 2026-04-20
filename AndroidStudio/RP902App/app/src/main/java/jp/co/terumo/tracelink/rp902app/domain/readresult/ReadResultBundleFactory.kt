package jp.co.terumo.tracelink.rp902app.domain.readresult

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementResult
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * Session snapshot と判定結果から、PostgreSQL function に渡す登録 bundle を作る契約。
 */
interface ReadResultBundleFactory {
    fun create(
        sessionId: String,
        registeredAtEpochMillis: Long,
        deviceId: String,
        readerType: String,
        workContext: WorkContext,
        ruleBundle: RuleBundle,
        equipmentSnapshot: EquipmentSnapshot,
        judgementResult: ReadJudgementResult,
        sessionTags: List<InventoryTag>,
    ): ReadResultRegistrationBundle
}
