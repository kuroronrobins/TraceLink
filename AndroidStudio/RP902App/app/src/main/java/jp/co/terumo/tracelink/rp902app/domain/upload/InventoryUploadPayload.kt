package jp.co.terumo.tracelink.rp902app.domain.upload

/**
 * backend へ送る inventory session 単位の payload。
 *
 * backend API はまだ未確定のため、現時点では app 側の暫定契約として扱う。
 * 本物 backend に合わせるときは `docs/architecture/BackendContract.md` も確認する。
 */
data class InventoryUploadPayload(
    val sessionId: String,
    val sentAtEpochMillis: Long,
    val deviceId: String,
    val readerType: String,
    val tags: List<InventoryUploadTag>,
)

/** payload 内のタグ 1 件。`readCount` は同一 EPC が session 内で読まれた回数。 */
data class InventoryUploadTag(
    val epc: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val readCount: Int,
)
