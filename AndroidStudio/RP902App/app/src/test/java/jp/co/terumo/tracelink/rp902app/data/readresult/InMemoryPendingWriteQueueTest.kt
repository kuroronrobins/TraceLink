package jp.co.terumo.tracelink.rp902app.data.readresult

import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryPendingWriteQueueTest {
    @Test
    fun enqueueFailure_addsPendingWriteAndDeduplicatesBySessionId() = runBlocking {
        val queue = InMemoryPendingWriteQueue()
        val bundle = bundle(sessionId = "session-1")

        queue.enqueueFailure(
            bundle = bundle,
            failedAtEpochMillis = 1000L,
            message = "network down",
        )
        queue.enqueueFailure(
            bundle = bundle,
            failedAtEpochMillis = 2000L,
            message = "still down",
        )

        val pending = queue.pendingWrites.value.single()

        assertEquals(1, queue.pendingWrites.value.size)
        assertEquals("session-1", pending.id)
        assertEquals(1000L, pending.queuedAtEpochMillis)
        assertEquals(2000L, pending.lastAttemptAtEpochMillis)
        assertEquals(2, pending.attemptCount)
        assertEquals("still down", pending.lastErrorMessage)
    }

    @Test
    fun remove_removesQueuedBundle() = runBlocking {
        val queue = InMemoryPendingWriteQueue()

        queue.enqueueFailure(
            bundle = bundle(sessionId = "session-1"),
            failedAtEpochMillis = 1000L,
            message = "network down",
        )
        queue.remove("session-1")

        assertEquals(emptyList<Any>(), queue.pendingWrites.value)
    }

    private fun bundle(sessionId: String): ReadResultRegistrationBundle = ReadResultRegistrationBundle(
        sessionId = sessionId,
        registeredAtEpochMillis = 900L,
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
                firstSeenAtEpochMillis = 100L,
                lastSeenAtEpochMillis = 200L,
                readCount = 2,
                judgementStatus = ReadJudgementStatus.Accepted,
                judgementReasonCode = null,
            ),
        ),
    )
}
