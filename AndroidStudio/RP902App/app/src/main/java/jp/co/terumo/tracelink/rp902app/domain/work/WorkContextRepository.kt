package jp.co.terumo.tracelink.rp902app.domain.work

/**
 * 読取作業の対象を表す context。
 *
 * XCgate 帳票や現場操作から渡される識別子を、Android 側の判定・登録処理で使う単位へそろえる。
 */
data class WorkContext(
    val workId: String,
    val reportId: String,
    val operatorId: String?,
    val startedAtEpochMillis: Long,
)

/**
 * Android が PostgreSQL から作業対象を取得するための契約。
 *
 * 実装は PostgreSQL view/function を使う想定で、UI はこの契約を直接呼ばない。
 */
interface WorkContextRepository {
    suspend fun resolveCurrentWorkContext(): WorkContext
}
