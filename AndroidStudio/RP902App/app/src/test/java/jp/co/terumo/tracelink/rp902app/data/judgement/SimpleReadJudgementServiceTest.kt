package jp.co.terumo.tracelink.rp902app.data.judgement

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentRecord
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SimpleReadJudgementServiceTest {
    private val service = SimpleReadJudgementService()

    @Test
    fun judge_acceptsTagWhenEquipmentExistsAndTypeIsAllowed() {
        val result = service.judge(
            workContext = workContext(),
            ruleBundle = ruleBundle(allowedTypes = setOf("traceable-equipment")),
            equipmentSnapshot = equipmentSnapshot(
                equipmentType = "traceable-equipment",
            ),
            sessionTags = listOf(tag("E2806894000040035A1F90A1")),
        )

        val entry = result.entries.single()

        assertEquals("E2806894000040035A1F90A1", entry.epc)
        assertEquals(ReadJudgementStatus.Accepted, entry.status)
        assertEquals(null, entry.reasonCode)
    }

    @Test
    fun judge_marksUnknownEquipmentAsNg() {
        val result = service.judge(
            workContext = workContext(),
            ruleBundle = ruleBundle(allowedTypes = setOf("traceable-equipment")),
            equipmentSnapshot = EquipmentSnapshot(
                snapshotVersion = "equipment-v1",
                capturedAtEpochMillis = 1000L,
                records = emptyList(),
            ),
            sessionTags = listOf(tag("E2806894000040035A1F90A1")),
        )

        val entry = result.entries.single()

        assertEquals(ReadJudgementStatus.Ng, entry.status)
        assertEquals("equipment_not_found", entry.reasonCode)
    }

    @Test
    fun judge_excludesEquipmentTypeOutsideRule() {
        val result = service.judge(
            workContext = workContext(),
            ruleBundle = ruleBundle(allowedTypes = setOf("target-type")),
            equipmentSnapshot = equipmentSnapshot(equipmentType = "other-type"),
            sessionTags = listOf(tag("E2806894000040035A1F90A1")),
        )

        val entry = result.entries.single()

        assertEquals(ReadJudgementStatus.Excluded, entry.status)
        assertEquals("equipment_type_not_allowed", entry.reasonCode)
    }

    @Test
    fun judge_rejectsRuleForDifferentReport() {
        assertThrows(IllegalArgumentException::class.java) {
            service.judge(
                workContext = workContext(),
                ruleBundle = ruleBundle(reportId = "other-report"),
                equipmentSnapshot = equipmentSnapshot(equipmentType = "traceable-equipment"),
                sessionTags = listOf(tag("E2806894000040035A1F90A1")),
            )
        }
    }

    private fun workContext(): WorkContext = WorkContext(
        workId = "work-1",
        reportId = "report-1",
        operatorId = "operator-1",
        startedAtEpochMillis = 1000L,
    )

    private fun ruleBundle(
        reportId: String = "report-1",
        allowedTypes: Set<String> = setOf("traceable-equipment"),
    ): RuleBundle = RuleBundle(
        ruleVersion = "rule-v1",
        reportId = reportId,
        effectiveAtEpochMillis = 1000L,
        targetEquipmentTypes = allowedTypes,
    )

    private fun equipmentSnapshot(equipmentType: String): EquipmentSnapshot = EquipmentSnapshot(
        snapshotVersion = "equipment-v1",
        capturedAtEpochMillis = 1000L,
        records = listOf(
            EquipmentRecord(
                equipmentId = "equipment-1",
                epc = "e2806894000040035a1f90a1",
                equipmentType = equipmentType,
                displayName = "Equipment 1",
            ),
        ),
    )

    private fun tag(epc: String): InventoryTag = InventoryTag(
        epc = epc,
        firstSeenAtEpochMillis = 100L,
        lastSeenAtEpochMillis = 200L,
        readCount = 2,
    )
}
