package jp.co.terumo.tracelink.rp902app.data.log

import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.log.EventLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 構造化ログをメモリ上に保持する簡易実装。
 *
 * まだ永続化はしないが、`EventLogStore` 契約を挟んでいるため、
 * 将来ファイル保存や DB 保存へ差し替えるときも UI/Repository の変更を小さくできる。
 */
class InMemoryEventLogStore(
    private val maxEntries: Int = DefaultMaxEntries,
) : EventLogStore {
    private val _logs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    override val logs: StateFlow<List<AppLogEntry>> = _logs.asStateFlow()

    private var nextId = 1L

    override suspend fun append(
        occurredAtEpochMillis: Long,
        level: AppLogLevel,
        category: AppLogCategory,
        message: String,
    ) {
        val entry = AppLogEntry(
            id = nextId++,
            occurredAtEpochMillis = occurredAtEpochMillis,
            level = level,
            category = category,
            message = message,
        )
        // 新しいログを先頭に置き、画面で直近イベントを追いやすくする。
        // maxEntries を超えた古いログは in-memory 実装では保持しない。
        _logs.update { current ->
            (listOf(entry) + current).take(maxEntries)
        }
    }

    /** 現在保持しているログをすべて消す。永続化実装に差し替える場合も同じ契約を保つ。 */
    override suspend fun clear() {
        _logs.value = emptyList()
    }

    private companion object {
        const val DefaultMaxEntries = 200
    }
}
