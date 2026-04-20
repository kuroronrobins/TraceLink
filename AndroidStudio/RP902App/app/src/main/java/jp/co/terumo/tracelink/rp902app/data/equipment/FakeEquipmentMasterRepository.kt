package jp.co.terumo.tracelink.rp902app.data.equipment

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentRecord
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * fake reader の EPC に対応する固定設備 master snapshot。
 */
class FakeEquipmentMasterRepository(
    private val records: List<EquipmentRecord> = defaultRecords,
) : EquipmentMasterRepository {
    override suspend fun fetchEquipmentSnapshot(workContext: WorkContext): EquipmentSnapshot =
        EquipmentSnapshot(
            snapshotVersion = "fake-equipment-snapshot-v1",
            capturedAtEpochMillis = workContext.startedAtEpochMillis,
            records = records,
        )

    companion object {
        private val defaultRecords = listOf(
            EquipmentRecord(
                equipmentId = "fake-equipment-001",
                epc = "E2806894000040035A1F90A1",
                equipmentType = "traceable-equipment",
                displayName = "Fake Equipment 001",
            ),
            EquipmentRecord(
                equipmentId = "fake-equipment-002",
                epc = "E2806894000050035A1F90A2",
                equipmentType = "traceable-equipment",
                displayName = "Fake Equipment 002",
            ),
            EquipmentRecord(
                equipmentId = "fake-equipment-003",
                epc = "E2806894000060035A1F90A3",
                equipmentType = "traceable-equipment",
                displayName = "Fake Equipment 003",
            ),
            EquipmentRecord(
                equipmentId = "fake-equipment-004",
                epc = "E2806894000070035A1F90A4",
                equipmentType = "traceable-equipment",
                displayName = "Fake Equipment 004",
            ),
        )
    }
}
