package jp.co.terumo.tracelink.rp902app.domain.log

import kotlinx.coroutines.flow.StateFlow

/**
 * structured log 保存先の契約。
 *
 * 現在は in-memory 実装だが、将来 log export や永続保存が必要になっても
 * Repository/UI 側はこの契約を見続ければよい。
 */
interface EventLogStore {
    val logs: StateFlow<List<AppLogEntry>>

    suspend fun append(
        occurredAtEpochMillis: Long,
        level: AppLogLevel,
        category: AppLogCategory,
        message: String,
    )

    suspend fun clear()
}
