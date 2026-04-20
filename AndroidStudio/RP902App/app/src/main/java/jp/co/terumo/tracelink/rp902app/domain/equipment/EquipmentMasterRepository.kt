package jp.co.terumo.tracelink.rp902app.domain.equipment

import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * 端末内判定で参照する設備マスタの snapshot。
 */
data class EquipmentSnapshot(
    val snapshotVersion: String,
    val capturedAtEpochMillis: Long,
    val records: List<EquipmentRecord>,
)

data class EquipmentRecord(
    val equipmentId: String,
    val epc: String,
    val equipmentType: String,
    val displayName: String,
)

/**
 * Android が PostgreSQL から設備マスタを取得するための契約。
 */
interface EquipmentMasterRepository {
    suspend fun fetchEquipmentSnapshot(workContext: WorkContext): EquipmentSnapshot
}
