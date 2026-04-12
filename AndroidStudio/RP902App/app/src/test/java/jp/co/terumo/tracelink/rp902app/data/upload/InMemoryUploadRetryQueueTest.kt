package jp.co.terumo.tracelink.rp902app.data.upload

import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryUploadRetryQueueTest {
    @Test
    fun enqueueFailure_addsPendingUploadAndDeduplicatesBySessionId() = runBlocking {
        val queue = InMemoryUploadRetryQueue()
        val payload = payload(sessionId = "session-1")

        queue.enqueueFailure(
            payload = payload,
            failedAtEpochMillis = 1000L,
            message = "network down",
        )
        queue.enqueueFailure(
            payload = payload,
            failedAtEpochMillis = 2000L,
            message = "still down",
        )

        val pending = queue.pendingUploads.value.single()

        assertEquals(1, queue.pendingUploads.value.size)
        assertEquals("session-1", pending.id)
        assertEquals(1000L, pending.queuedAtEpochMillis)
        assertEquals(2000L, pending.lastAttemptAtEpochMillis)
        assertEquals(2, pending.attemptCount)
        assertEquals("still down", pending.lastErrorMessage)
    }

    @Test
    fun remove_removesQueuedPayload() = runBlocking {
        val queue = InMemoryUploadRetryQueue()

        queue.enqueueFailure(
            payload = payload(sessionId = "session-1"),
            failedAtEpochMillis = 1000L,
            message = "network down",
        )
        queue.remove("session-1")

        assertEquals(emptyList<Any>(), queue.pendingUploads.value)
    }

    private fun payload(sessionId: String): InventoryUploadPayload = InventoryUploadPayload(
        sessionId = sessionId,
        sentAtEpochMillis = 900L,
        deviceId = "device-1",
        readerType = "RP902",
        tags = listOf(
            InventoryUploadTag(
                epc = "E2806894000040035A1F90A1",
                firstSeenAtEpochMillis = 100L,
                lastSeenAtEpochMillis = 200L,
                readCount = 2,
            ),
        ),
    )
}
