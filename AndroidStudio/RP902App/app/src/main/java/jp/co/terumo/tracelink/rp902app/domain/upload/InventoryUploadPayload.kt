package jp.co.terumo.tracelink.rp902app.domain.upload

data class InventoryUploadPayload(
    val sessionId: String,
    val sentAtEpochMillis: Long,
    val deviceId: String,
    val readerType: String,
    val tags: List<InventoryUploadTag>,
)

data class InventoryUploadTag(
    val epc: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val readCount: Int,
)

