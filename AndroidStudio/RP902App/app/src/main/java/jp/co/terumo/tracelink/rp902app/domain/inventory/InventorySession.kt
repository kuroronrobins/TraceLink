package jp.co.terumo.tracelink.rp902app.domain.inventory

import java.util.Locale
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead

/**
 * 1 回の読取 session 内で EPC を正規化し、重複を取り除く純粋ロジック。
 *
 * DB 登録、rule/master 取得、判定、Android API には依存させない。
 */
class InventorySession {
    private val tagsByEpc = linkedMapOf<String, InventoryTag>()

    /**
     * 1 回分の読取を session に反映し、重複除去後の snapshot を返す。
     *
     * EPC 正規化は bundle 生成や判定の前提になるため、ここで一元化する。
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

    fun clear() {
        tagsByEpc.clear()
    }

    private fun normalizeEpc(epc: String): String = epc.trim().uppercase(Locale.US)
}
