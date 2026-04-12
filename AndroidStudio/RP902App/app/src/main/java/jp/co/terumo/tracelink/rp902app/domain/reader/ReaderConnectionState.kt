package jp.co.terumo.tracelink.rp902app.domain.reader

sealed interface ReaderConnectionState {
    data object Disconnected : ReaderConnectionState
    data object Connecting : ReaderConnectionState
    data object Connected : ReaderConnectionState
    data class Error(val message: String) : ReaderConnectionState
}

fun ReaderConnectionState.displayText(): String = when (this) {
    ReaderConnectionState.Disconnected -> "Disconnected"
    ReaderConnectionState.Connecting -> "Connecting"
    ReaderConnectionState.Connected -> "Connected"
    is ReaderConnectionState.Error -> "Error: $message"
}

