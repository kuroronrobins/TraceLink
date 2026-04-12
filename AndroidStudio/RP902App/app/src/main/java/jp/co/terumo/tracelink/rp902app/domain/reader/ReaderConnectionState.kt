package jp.co.terumo.tracelink.rp902app.domain.reader

/**
 * reader の接続状態。
 *
 * fake/real のどちらでも同じ状態モデルを使い、画面や repository が vendor SDK の状態 enum を
 * 直接知らなくてよいようにする。
 */
sealed interface ReaderConnectionState {
    data object Disconnected : ReaderConnectionState
    data object Connecting : ReaderConnectionState
    data object Connected : ReaderConnectionState
    data class Error(val message: String) : ReaderConnectionState
}

/** 画面表示用の短い文言へ変換する helper。業務判断には使わない。 */
fun ReaderConnectionState.displayText(): String = when (this) {
    ReaderConnectionState.Disconnected -> "Disconnected"
    ReaderConnectionState.Connecting -> "Connecting"
    ReaderConnectionState.Connected -> "Connected"
    is ReaderConnectionState.Error -> "Error: $message"
}
