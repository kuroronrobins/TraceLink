package jp.co.terumo.tracelink.rp902app.domain.readresult

import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus

/**
 * PostgreSQL function へ渡す判定済み読取結果の登録単位。
 *
 * Android 側で work context / rule / equipment / judgement をそろえてから作る。
 * DB 実装はこの bundle を PostgreSQL function へ渡し、table 直叩きの知識を上位へ漏らさない。
 */
data class ReadResultRegistrationBundle(
    val sessionId: String,
    val registeredAtEpochMillis: Long,
    val deviceId: String,
    val readerType: String,
    val workId: String,
    val reportId: String,
    val operatorId: String?,
    val ruleVersion: String,
    val equipmentSnapshotVersion: String,
    val tags: List<ReadResultTag>,
)

/** 登録 bundle 内のタグ 1 件。`readCount` は同一 EPC が session 内で読まれた回数。 */
data class ReadResultTag(
    val epc: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val readCount: Int,
    val judgementStatus: ReadJudgementStatus,
    val judgementReasonCode: String?,
)
