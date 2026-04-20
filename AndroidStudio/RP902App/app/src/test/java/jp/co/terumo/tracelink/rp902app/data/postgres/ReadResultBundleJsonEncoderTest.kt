package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadResultBundleJsonEncoderTest {
    @Test
    fun encode_serializesRegistrationBundleForJsonbFunctionArgument() {
        val json = ReadResultBundleJsonEncoder.encode(bundle())

        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.contains("\"sessionId\":\"session-1\""))
        assertTrue(json.contains("\"registeredAtEpochMillis\":3000"))
        assertTrue(json.contains("\"workId\":\"work-1\""))
        assertTrue(json.contains("\"ruleVersion\":\"rule-v1\""))
        assertTrue(json.contains("\"equipmentSnapshotVersion\":\"equipment-v1\""))
        assertTrue(json.contains("\"judgementStatus\":\"Accepted\""))
        assertTrue(json.contains("\"judgementReasonCode\":null"))
    }

    @Test
    fun encode_escapesStringValues() {
        val json = ReadResultBundleJsonEncoder.encode(
            bundle().copy(
                sessionId = "session-\"quoted\"",
                operatorId = "operator\\one",
            ),
        )

        assertTrue(json.contains("\"sessionId\":\"session-\\\"quoted\\\"\""))
        assertTrue(json.contains("\"operatorId\":\"operator\\\\one\""))
    }

    @Test
    fun encode_isStableForSingleTagBundle() {
        val json = ReadResultBundleJsonEncoder.encode(bundle())

        assertEquals(1, "\"tags\":\\[".toRegex().findAll(json).count())
        assertEquals(1, "\"epc\":\"E2806894000040035A1F90A1\"".toRegex().findAll(json).count())
    }

    private fun bundle(): ReadResultRegistrationBundle =
        ReadResultRegistrationBundle(
            sessionId = "session-1",
            registeredAtEpochMillis = 3000L,
            deviceId = "device-1",
            readerType = "RP902",
            workId = "work-1",
            reportId = "report-1",
            operatorId = "operator-1",
            ruleVersion = "rule-v1",
            equipmentSnapshotVersion = "equipment-v1",
            tags = listOf(
                ReadResultTag(
                    epc = "E2806894000040035A1F90A1",
                    firstSeenAtEpochMillis = 1000L,
                    lastSeenAtEpochMillis = 2000L,
                    readCount = 2,
                    judgementStatus = ReadJudgementStatus.Accepted,
                    judgementReasonCode = null,
                ),
            ),
        )
}
