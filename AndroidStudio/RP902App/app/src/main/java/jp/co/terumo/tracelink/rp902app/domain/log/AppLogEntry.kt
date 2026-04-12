package jp.co.terumo.tracelink.rp902app.domain.log

data class AppLogEntry(
    val id: Long,
    val occurredAtEpochMillis: Long,
    val level: AppLogLevel,
    val category: AppLogCategory,
    val message: String,
)

enum class AppLogLevel {
    Info,
    Warning,
    Error,
}

enum class AppLogCategory {
    System,
    Reader,
    Inventory,
    Upload,
}
