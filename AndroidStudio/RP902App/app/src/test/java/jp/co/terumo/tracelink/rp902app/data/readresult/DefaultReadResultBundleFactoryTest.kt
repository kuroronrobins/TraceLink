package jp.co.terumo.tracelink.rp902app.data.readresult

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.JudgedReadEntry
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementResult
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultReadResultBundleFactoryTest {
    private val factory = DefaultReadResultBundleFactory()

    @Test
    fun create_combinesContextRuleEquipmentAndJudgementIntoBundle() {
        val bundle = factory.create(
            sessionId = "session-1",
            registeredAtEpochMillis = 3000L,
            deviceId = "device-1",
            readerType = "RP902",
            workContext = workContext(),
            ruleBundle = ruleBundle(),
            equipmentSnapshot = equipmentSnapshot(),
            judgementResult = judgementResult(),
            sessionTags = sessionTags(),
        )

        assertEquals("session-1", bundle.sessionId)
        assertEquals("work-1", bundle.workId)
        assertEquals("report-1", bundle.reportId)
        assertEquals("operator-1", bundle.operatorId)
        assertEquals("rule-v1", bundle.ruleVersion)
        assertEquals("equipment-v1", bundle.equipmentSnapshotVersion)
        assertEquals(ReadJudgementStatus.Accepted, bundle.tags.single().judgementStatus)
    }

    @Test
    fun create_rejectsSessionTagsWithoutJudgement() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(
                sessionId = "session-1",
                registeredAtEpochMillis = 3000L,
                deviceId = "device-1",
                readerType = "RP902",
                workContext = workContext(),
                ruleBundle = ruleBundle(),
                equipmentSnapshot = equipmentSnapshot(),
                judgementResult = ReadJudgementResult(entries = emptyList()),
                sessionTags = sessionTags(),
            )
        }
    }

    private fun workContext(): WorkContext = WorkContext(
        workId = "work-1",
        reportId = "report-1",
        operatorId = "operator-1",
        startedAtEpochMillis = 1000L,
    )

    private fun ruleBundle(): RuleBundle = RuleBundle(
        ruleVersion = "rule-v1",
        reportId = "report-1",
        effectiveAtEpochMillis = 1000L,
        targetEquipmentTypes = setOf("traceable-equipment"),
    )

    private fun equipmentSnapshot(): EquipmentSnapshot = EquipmentSnapshot(
        snapshotVersion = "equipment-v1",
        capturedAtEpochMillis = 1000L,
        records = emptyList(),
    )

    private fun judgementResult(): ReadJudgementResult = ReadJudgementResult(
        entries = listOf(
            JudgedReadEntry(
                epc = "E2806894000040035A1F90A1",
                status = ReadJudgementStatus.Accepted,
                reasonCode = null,
            ),
        ),
    )

    private fun sessionTags(): List<InventoryTag> = listOf(
        InventoryTag(
            epc = "E2806894000040035A1F90A1",
            firstSeenAtEpochMillis = 100L,
            lastSeenAtEpochMillis = 200L,
            readCount = 2,
        ),
    )
}
