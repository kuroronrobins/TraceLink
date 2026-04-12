package jp.co.terumo.tracelink.rp902app.domain.inventory

import java.util.Locale
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadTag

/**
 * 1 回の inventory session 内で読まれたタグを管理する純粋ロジック。
 *
 * 同じ EPC が何度読まれても画面上は 1 件にまとめ、`readCount` と最終読取時刻だけを更新する。
 * Android や vendor SDK に依存しないため、単体テストで重複除去の仕様を確認しやすい。
 */
class InventorySession {
    private val tagsByEpc = linkedMapOf<String, InventoryTag>()

    /**
     * 1 回分の読取を session に反映し、重複除去後の snapshot を返す。
     *
     * EPC は大文字へ正規化する。同じセッション内の重複判定キーなので、
     * ここを変える場合は upload payload とテストの期待値も確認する。
     */
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

    /** 現在の session を破棄する。upload retry queue は別責務なのでここでは触らない。 */
    fun clear() {
        tagsByEpc.clear()
    }

    /** 現在の session を backend upload 用 payload に変換する。 */
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
