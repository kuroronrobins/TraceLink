package jp.co.terumo.tracelink.rp902app.domain.reader

/**
 * reader から届いた「1 回分のタグ読取」。
 *
 * 同じ EPC が何度も流れてくる可能性があるため、重複除去は `InventorySession` が担当する。
 */
data class ReaderTagRead(
    val epc: String,
    val seenAtEpochMillis: Long,
)
