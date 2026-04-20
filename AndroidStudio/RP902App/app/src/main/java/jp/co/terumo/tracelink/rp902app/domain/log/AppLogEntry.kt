package jp.co.terumo.tracelink.rp902app.domain.log

/**
 * アプリ内で表示・保存する構造化ログ 1 件。
 *
 * 文字列だけではなく level/category を分けることで、
 * 実機調査時に Reader / Inventory / ResultRegistration のどこで起きた事象か追いやすくする。
 */
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
    ResultRegistration,
}
