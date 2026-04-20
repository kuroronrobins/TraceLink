package jp.co.terumo.tracelink.rp902app.data.readresult

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.JudgedReadEntry
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultBundleFactory
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultTag
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * 登録 bundle の構造を 1 箇所で固定する factory。
 *
 * `InventorySession` に DB 登録単位の知識を戻さないため、この class で snapshot と判定結果を結合する。
 */
class DefaultReadResultBundleFactory : ReadResultBundleFactory {
    override fun create(
        sessionId: String,
        registeredAtEpochMillis: Long,
        deviceId: String,
        readerType: String,
        workContext: WorkContext,
        ruleBundle: RuleBundle,
        equipmentSnapshot: EquipmentSnapshot,
        judgementResult: ReadJudgementResult,
        sessionTags: List<InventoryTag>,
    ): ReadResultRegistrationBundle {
        val judgementsByEpc = judgementResult.entries.associateBy { entry -> entry.epc }

        require(sessionTags.all { tag -> judgementsByEpc.containsKey(tag.epc) }) {
            "Every session tag must have a judgement entry."
        }

        return ReadResultRegistrationBundle(
            sessionId = sessionId,
            registeredAtEpochMillis = registeredAtEpochMillis,
            deviceId = deviceId,
            readerType = readerType,
            workId = workContext.workId,
            reportId = workContext.reportId,
            operatorId = workContext.operatorId,
            ruleVersion = ruleBundle.ruleVersion,
            equipmentSnapshotVersion = equipmentSnapshot.snapshotVersion,
            tags = sessionTags.map { tag ->
                tag.toReadResultTag(judgementsByEpc.getValue(tag.epc))
            },
        )
    }

    private fun InventoryTag.toReadResultTag(judgement: JudgedReadEntry): ReadResultTag =
        ReadResultTag(
            epc = epc,
            firstSeenAtEpochMillis = firstSeenAtEpochMillis,
            lastSeenAtEpochMillis = lastSeenAtEpochMillis,
            readCount = readCount,
            judgementStatus = judgement.status,
            judgementReasonCode = judgement.reasonCode,
        )
}
