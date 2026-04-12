package jp.co.terumo.tracelink.rp902app.domain.log

import kotlinx.coroutines.flow.StateFlow

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
