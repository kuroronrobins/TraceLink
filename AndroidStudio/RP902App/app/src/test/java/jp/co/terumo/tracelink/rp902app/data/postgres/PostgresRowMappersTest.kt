package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostgresRowMappersTest {
    @Test
    fun workContext_mapsRequiredFields() {
        val workContext = PostgresRowMappers.workContext(
            PostgresRow(
                mapOf(
                    PostgresColumns.WorkId to "work-1",
                    PostgresColumns.ReportId to "report-1",
                    PostgresColumns.OperatorId to "operator-1",
                    PostgresColumns.StartedAtEpochMillis to 1000L,
                ),
            ),
        )

        assertEquals("work-1", workContext.workId)
        assertEquals("report-1", workContext.reportId)
        assertEquals("operator-1", workContext.operatorId)
        assertEquals(1000L, workContext.startedAtEpochMillis)
    }

    @Test
    fun ruleBundle_mapsArrayLikeEquipmentTypes() {
        val ruleBundle = PostgresRowMappers.ruleBundle(
            PostgresRow(
                mapOf(
                    PostgresColumns.RuleVersion to "rule-v1",
                    PostgresColumns.ReportId to "report-1",
                    PostgresColumns.EffectiveAtEpochMillis to 1000L,
                    PostgresColumns.TargetEquipmentTypes to arrayOf("type-a", "type-b"),
                ),
            ),
        )

        assertEquals("rule-v1", ruleBundle.ruleVersion)
        assertEquals(setOf("type-a", "type-b"), ruleBundle.targetEquipmentTypes)
    }

    @Test
    fun equipmentSnapshot_mapsRowsIntoRecords() {
        val snapshot = PostgresRowMappers.equipmentSnapshot(
            listOf(
                equipmentRow("equipment-1", "EPC-1"),
                equipmentRow("equipment-2", "EPC-2"),
            ),
        )

        assertEquals("snapshot-v1", snapshot.snapshotVersion)
        assertEquals(2, snapshot.records.size)
        assertEquals("equipment-1", snapshot.records[0].equipmentId)
        assertEquals("EPC-2", snapshot.records[1].epc)
    }

    @Test
    fun registrationResult_mapsRejectedFunctionResultToContractFailure() {
        val result = PostgresRowMappers.registrationResult(
            PostgresRow(
                mapOf(
                    PostgresColumns.Success to false,
                    PostgresColumns.Duplicate to false,
                    PostgresColumns.AcceptedSessionId to null,
                    PostgresColumns.ResultId to null,
                    PostgresColumns.FailureKind to "contract",
                    PostgresColumns.ErrorCode to "invalid_json_schema",
                    PostgresColumns.Message to "schema rejected bundle",
                ),
            ),
        )

        assertTrue(result is ReadResultRegistrationResult.Failure)
        result as ReadResultRegistrationResult.Failure
        assertEquals("schema rejected bundle", result.message)
        assertEquals(RegistrationFailureKind.Contract, result.kind)
        assertEquals("invalid_json_schema", result.errorCode)
    }

    @Test
    fun registrationResult_mapsDuplicateSuccessMetadata() {
        val result = PostgresRowMappers.registrationResult(
            PostgresRow(
                mapOf(
                    PostgresColumns.Success to true,
                    PostgresColumns.Duplicate to true,
                    PostgresColumns.AcceptedSessionId to "session-1",
                    PostgresColumns.ResultId to "00000000-0000-0000-0000-000000000001",
                    PostgresColumns.FailureKind to null,
                    PostgresColumns.ErrorCode to null,
                    PostgresColumns.Message to "already registered",
                ),
            ),
        )

        assertTrue(result is ReadResultRegistrationResult.Success)
        result as ReadResultRegistrationResult.Success
        assertEquals(true, result.duplicate)
        assertEquals("session-1", result.acceptedSessionId)
        assertEquals("00000000-0000-0000-0000-000000000001", result.resultId)
    }

    private fun equipmentRow(equipmentId: String, epc: String): PostgresRow =
        PostgresRow(
            mapOf(
                PostgresColumns.SnapshotVersion to "snapshot-v1",
                PostgresColumns.CapturedAtEpochMillis to 2000L,
                PostgresColumns.EquipmentId to equipmentId,
                PostgresColumns.Epc to epc,
                PostgresColumns.EquipmentType to "traceable-equipment",
                PostgresColumns.DisplayName to "Equipment $equipmentId",
            ),
        )
}
