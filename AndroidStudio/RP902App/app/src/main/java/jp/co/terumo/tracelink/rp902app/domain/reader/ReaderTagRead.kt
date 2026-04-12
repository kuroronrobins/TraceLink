package jp.co.terumo.tracelink.rp902app.domain.reader

data class ReaderTagRead(
    val epc: String,
    val seenAtEpochMillis: Long,
)

