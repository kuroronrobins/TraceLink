package jp.co.terumo.tracelink.rp902app.domain.inventory

import java.util.Locale
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadTag

class InventorySession {
    private val tagsByEpc = linkedMapOf<String, InventoryTag>()

    fun record(read: ReaderTagRead): List<InventoryTag> {
        val epc = normalizeEpc(read.epc)
        require(epc.isNotBlank()) { "EPC must not be blank." }

        val updated = tagsByEpc[epc]?.let { existing ->
            existing.copy(
                lastSeenAtEpochMillis = read.seenAtEpochMillis,
                readCount = existing.readCount + 1,
            )
        } ?: InventoryTag(
            epc = epc,
            firstSeenAtEpochMillis = read.seenAtEpochMillis,
            lastSeenAtEpochMillis = read.seenAtEpochMillis,
            readCount = 1,
        )

        tagsByEpc[epc] = updated
        return snapshot()
    }

    fun snapshot(): List<InventoryTag> = tagsByEpc.values.toList()

    fun clear() {
        tagsByEpc.clear()
    }

    fun toUploadPayload(
        sessionId: String,
        sentAtEpochMillis: Long,
        deviceId: String,
        readerType: String,
    ): InventoryUploadPayload = InventoryUploadPayload(
        sessionId = sessionId,
        sentAtEpochMillis = sentAtEpochMillis,
        deviceId = deviceId,
        readerType = readerType,
        tags = snapshot().map { tag ->
            InventoryUploadTag(
                epc = tag.epc,
                firstSeenAtEpochMillis = tag.firstSeenAtEpochMillis,
                lastSeenAtEpochMillis = tag.lastSeenAtEpochMillis,
                readCount = tag.readCount,
            )
        },
    )

    private fun normalizeEpc(epc: String): String = epc.trim().uppercase(Locale.US)
}

