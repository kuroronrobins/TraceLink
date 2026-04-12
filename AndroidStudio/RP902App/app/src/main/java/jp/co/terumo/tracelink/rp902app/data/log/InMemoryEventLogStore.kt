package jp.co.terumo.tracelink.rp902app.data.log

import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.log.EventLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        _logs.update { current ->
            (listOf(entry) + current).take(maxEntries)
        }
    }

    override suspend fun clear() {
        _logs.value = emptyList()
    }

    private companion object {
        const val DefaultMaxEntries = 200
    }
}
