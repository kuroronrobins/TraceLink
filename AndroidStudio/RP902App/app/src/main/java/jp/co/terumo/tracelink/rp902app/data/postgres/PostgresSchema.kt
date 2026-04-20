package jp.co.terumo.tracelink.rp902app.data.postgres

internal object PostgresSchema {
    val getActiveWorkContextFunction = QualifiedPostgresName("api", "fn_get_active_work_context")
    val getRuleBundleFunction = QualifiedPostgresName("api", "fn_get_rule_bundle")
    val getEquipmentSnapshotFunction = QualifiedPostgresName("api", "fn_get_equipment_snapshot")
    val registerReadResultBundleFunction = QualifiedPostgresName("api", "fn_register_read_result_bundle")
}

internal data class QualifiedPostgresName(
    val schema: String,
    val name: String,
) {
    init {
        require(identifierRegex.matches(schema)) { "Invalid PostgreSQL schema name: $schema" }
        require(identifierRegex.matches(name)) { "Invalid PostgreSQL object name: $name" }
    }

    fun sql(): String = "$schema.$name"

    private companion object {
        private val identifierRegex = Regex("[a-z][a-z0-9_]*")
    }
}

internal object PostgresColumns {
    const val WorkId = "work_id"
    const val ReportId = "report_id"
    const val OperatorId = "operator_id"
    const val StartedAtEpochMillis = "started_at_epoch_millis"
    const val RuleVersion = "rule_version"
    const val EffectiveAtEpochMillis = "effective_at_epoch_millis"
    const val TargetEquipmentTypes = "target_equipment_types"
    const val SnapshotVersion = "snapshot_version"
    const val CapturedAtEpochMillis = "captured_at_epoch_millis"
    const val EquipmentId = "equipment_id"
    const val Epc = "epc"
    const val EquipmentType = "equipment_type"
    const val DisplayName = "display_name"
    const val Success = "success"
    const val Duplicate = "duplicate"
    const val AcceptedSessionId = "accepted_session_id"
    const val ResultId = "result_id"
    const val FailureKind = "failure_kind"
    const val ErrorCode = "error_code"
    const val Message = "message"
}

internal object PostgresSqlStatements {
    val FetchWorkContext = """
        select
            ${PostgresColumns.WorkId},
            ${PostgresColumns.ReportId},
            ${PostgresColumns.OperatorId},
            ${PostgresColumns.StartedAtEpochMillis}
        from ${PostgresSchema.getActiveWorkContextFunction.sql()}(?)
    """.trimIndent()

    val FetchRuleBundle = """
        select
            ${PostgresColumns.RuleVersion},
            ${PostgresColumns.ReportId},
            ${PostgresColumns.EffectiveAtEpochMillis},
            ${PostgresColumns.TargetEquipmentTypes}
        from ${PostgresSchema.getRuleBundleFunction.sql()}(?)
    """.trimIndent()

    val FetchEquipmentSnapshot = """
        select
            ${PostgresColumns.SnapshotVersion},
            ${PostgresColumns.CapturedAtEpochMillis},
            ${PostgresColumns.EquipmentId},
            ${PostgresColumns.Epc},
            ${PostgresColumns.EquipmentType},
            ${PostgresColumns.DisplayName}
        from ${PostgresSchema.getEquipmentSnapshotFunction.sql()}(?)
    """.trimIndent()

    val RegisterReadResultBundle = """
        select
            ${PostgresColumns.Success},
            ${PostgresColumns.Duplicate},
            ${PostgresColumns.AcceptedSessionId},
            ${PostgresColumns.ResultId},
            ${PostgresColumns.FailureKind},
            ${PostgresColumns.ErrorCode},
            ${PostgresColumns.Message}
        from ${PostgresSchema.registerReadResultBundleFunction.sql()}(?::jsonb)
    """.trimIndent()
}
