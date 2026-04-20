package jp.co.terumo.tracelink.rp902app.data.postgres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostgresSchemaTest {
    @Test
    fun objectNames_areCentralizedWithApiSchema() {
        assertEquals("api.v_active_work_context", PostgresSchema.activeWorkContextView.sql())
        assertEquals("api.fn_get_rule_bundle", PostgresSchema.getRuleBundleFunction.sql())
        assertEquals("api.fn_get_equipment_snapshot", PostgresSchema.getEquipmentSnapshotFunction.sql())
        assertEquals(
            "api.fn_register_read_result_bundle",
            PostgresSchema.registerReadResultBundleFunction.sql(),
        )
    }

    @Test
    fun sqlStatements_referenceCentralizedNames() {
        assertTrue(PostgresSqlStatements.FetchWorkContext.contains("api.v_active_work_context"))
        assertTrue(PostgresSqlStatements.FetchRuleBundle.contains("api.fn_get_rule_bundle"))
        assertTrue(PostgresSqlStatements.FetchEquipmentSnapshot.contains("api.fn_get_equipment_snapshot"))
        assertTrue(
            PostgresSqlStatements.RegisterReadResultBundle.contains(
                "api.fn_register_read_result_bundle",
            ),
        )
    }
}
