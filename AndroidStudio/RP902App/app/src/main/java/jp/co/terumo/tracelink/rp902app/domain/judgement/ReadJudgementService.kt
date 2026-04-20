package jp.co.terumo.tracelink.rp902app.domain.judgement

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * Android 端末内で RFID 読取結果を判定する純粋サービス。
 *
 * PostgreSQL から取得した rule/master snapshot と session 内 read を入力にし、
 * DB 登録前の判定結果を作る。
 */
interface ReadJudgementService {
    fun judge(
        workContext: WorkContext,
        ruleBundle: RuleBundle,
        equipmentSnapshot: EquipmentSnapshot,
        sessionTags: List<InventoryTag>,
    ): ReadJudgementResult
}

data class ReadJudgementResult(
    val entries: List<JudgedReadEntry>,
)

data class JudgedReadEntry(
    val epc: String,
    val status: ReadJudgementStatus,
    val reasonCode: String?,
)

enum class ReadJudgementStatus {
    Accepted,
    Excluded,
    Ng,
}
