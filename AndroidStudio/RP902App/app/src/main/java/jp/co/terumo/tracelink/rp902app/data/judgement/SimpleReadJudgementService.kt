package jp.co.terumo.tracelink.rp902app.data.judgement

import java.util.Locale
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentRecord
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.JudgedReadEntry
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementResult
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementService
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * 現段階の最小判定実装。
 *
 * ルール詳細が固まるまでは、設備 master に存在し、かつ rule の対象設備種別に含まれることだけを見る。
 */
class SimpleReadJudgementService : ReadJudgementService {
    override fun judge(
        workContext: WorkContext,
        ruleBundle: RuleBundle,
        equipmentSnapshot: EquipmentSnapshot,
        sessionTags: List<InventoryTag>,
    ): ReadJudgementResult {
        require(ruleBundle.reportId == workContext.reportId) {
            "Rule bundle reportId must match work context reportId."
        }

        val equipmentByEpc = equipmentSnapshot.records.associateBy { record -> record.normalizedEpc() }

        return ReadJudgementResult(
            entries = sessionTags.map { tag ->
                judgeTag(
                    tag = tag,
                    equipment = equipmentByEpc[tag.epc],
                    allowedEquipmentTypes = ruleBundle.targetEquipmentTypes,
                )
            },
        )
    }

    private fun judgeTag(
        tag: InventoryTag,
        equipment: EquipmentRecord?,
        allowedEquipmentTypes: Set<String>,
    ): JudgedReadEntry {
        if (equipment == null) {
            return JudgedReadEntry(
                epc = tag.epc,
                status = ReadJudgementStatus.Ng,
                reasonCode = "equipment_not_found",
            )
        }

        if (equipment.equipmentType !in allowedEquipmentTypes) {
            return JudgedReadEntry(
                epc = tag.epc,
                status = ReadJudgementStatus.Excluded,
                reasonCode = "equipment_type_not_allowed",
            )
        }

        return JudgedReadEntry(
            epc = tag.epc,
            status = ReadJudgementStatus.Accepted,
            reasonCode = null,
        )
    }

    private fun EquipmentRecord.normalizedEpc(): String = epc.trim().uppercase(Locale.US)
}
