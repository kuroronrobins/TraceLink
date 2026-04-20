package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentRecord
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext

internal object PostgresRowMappers {
    val WorkContextColumns = listOf(
        PostgresColumns.WorkId,
        PostgresColumns.ReportId,
        PostgresColumns.OperatorId,
        PostgresColumns.StartedAtEpochMillis,
    )

    val RuleBundleColumns = listOf(
        PostgresColumns.RuleVersion,
        PostgresColumns.ReportId,
        PostgresColumns.EffectiveAtEpochMillis,
        PostgresColumns.TargetEquipmentTypes,
    )

    val EquipmentSnapshotColumns = listOf(
        PostgresColumns.SnapshotVersion,
        PostgresColumns.CapturedAtEpochMillis,
        PostgresColumns.EquipmentId,
        PostgresColumns.Epc,
        PostgresColumns.EquipmentType,
        PostgresColumns.DisplayName,
    )

    val RegistrationResultColumns = listOf(
        PostgresColumns.Success,
        PostgresColumns.Message,
    )

    fun workContext(row: PostgresRow): WorkContext =
        WorkContext(
            workId = row.requiredString(PostgresColumns.WorkId),
            reportId = row.requiredString(PostgresColumns.ReportId),
            operatorId = row.optionalString(PostgresColumns.OperatorId),
            startedAtEpochMillis = row.requiredLong(PostgresColumns.StartedAtEpochMillis),
        )

    fun ruleBundle(row: PostgresRow): RuleBundle =
        RuleBundle(
            ruleVersion = row.requiredString(PostgresColumns.RuleVersion),
            reportId = row.requiredString(PostgresColumns.ReportId),
            effectiveAtEpochMillis = row.requiredLong(PostgresColumns.EffectiveAtEpochMillis),
            targetEquipmentTypes = row.requiredStringSet(PostgresColumns.TargetEquipmentTypes),
        )

    fun equipmentSnapshot(rows: List<PostgresRow>): EquipmentSnapshot {
        require(rows.isNotEmpty()) {
            "Equipment snapshot function returned no rows; snapshot metadata is required."
        }

        val firstRow = rows.first()
        return EquipmentSnapshot(
            snapshotVersion = firstRow.requiredString(PostgresColumns.SnapshotVersion),
            capturedAtEpochMillis = firstRow.requiredLong(PostgresColumns.CapturedAtEpochMillis),
            records = rows.map(::equipmentRecord),
        )
    }

    fun registrationResult(row: PostgresRow): ReadResultRegistrationResult =
        if (row.requiredBoolean(PostgresColumns.Success)) {
            ReadResultRegistrationResult.Success
        } else {
            ReadResultRegistrationResult.Failure(
                message = row.optionalString(PostgresColumns.Message)
                    ?: "PostgreSQL rejected read result registration.",
                kind = RegistrationFailureKind.Contract,
            )
        }

    private fun equipmentRecord(row: PostgresRow): EquipmentRecord =
        EquipmentRecord(
            equipmentId = row.requiredString(PostgresColumns.EquipmentId),
            epc = row.requiredString(PostgresColumns.Epc),
            equipmentType = row.requiredString(PostgresColumns.EquipmentType),
            displayName = row.requiredString(PostgresColumns.DisplayName),
        )
}
