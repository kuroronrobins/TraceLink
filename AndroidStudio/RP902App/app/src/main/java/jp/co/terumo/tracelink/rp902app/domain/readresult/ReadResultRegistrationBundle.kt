package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * PostgreSQL function へ渡す読取結果登録単位。
 *
 * 現段階では実 DB 接続を入れず、Android 側が組み立てる登録単位を先に固定する。
 * 実装時は `docs/architecture/PostgreSQLAccessContract.md` の View / Function 境界に合わせる。
 */
data class ReadResultRegistrationBundle(
    val sessionId: String,
    val registeredAtEpochMillis: Long,
    val deviceId: String,
    val readerType: String,
    val tags: List<ReadResultTag>,
)

/** 登録 bundle 内のタグ 1 件。`readCount` は同一 EPC が session 内で読まれた回数。 */
data class ReadResultTag(
    val epc: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val readCount: Int,
)
