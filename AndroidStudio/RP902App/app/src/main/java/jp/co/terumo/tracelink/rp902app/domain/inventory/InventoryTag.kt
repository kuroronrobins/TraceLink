package jp.co.terumo.tracelink.rp902app.domain.inventory

data class InventoryTag(
    val epc: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val readCount: Int,
)

