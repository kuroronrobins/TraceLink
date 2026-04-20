package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGateway
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

/**
 * Equipment master repository の PostgreSQL 実装入口。
 */
class PostgresEquipmentMasterRepository(
    private val postgresGateway: PostgresGateway,
) : EquipmentMasterRepository {
    override suspend fun fetchEquipmentSnapshot(workContext: WorkContext): EquipmentSnapshot =
        postgresGateway.fetchEquipmentSnapshot(workContext)
}
