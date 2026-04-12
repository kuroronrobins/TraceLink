package jp.co.terumo.tracelink.rp902app.domain.reader

data class ReaderGatewayEvent(
    val level: ReaderGatewayEventLevel,
    val message: String,
)

enum class ReaderGatewayEventLevel {
    Info,
    Warning,
    Error,
}
