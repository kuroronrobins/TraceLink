package jp.co.terumo.tracelink.rp902app.domain.reader

import kotlinx.coroutines.flow.StateFlow

/**
 * reader 接続方法の設定。
 *
 * 既定の `gatewayMode` は fake なので、実機 RP902 がない環境でもアプリを起動できる。
 * 今回運用では RP902 が 1 台固定のため、Bluetooth MAC は既定値を持たせて初回入力を省く。
 * ただし `readerBluetoothAddress` は更新可能で、将来別 reader を使う場合も Settings から変更できる。
 */
data class ReaderSettings(
    val gatewayMode: ReaderGatewayMode = ReaderGatewayMode.Fake,
    val readerBluetoothAddress: ReaderBluetoothAddress? = ReaderBluetoothAddress.DefaultRp902,
)

/** reader settings の保存先契約。現在の実装は in-memory、将来は永続化へ差し替える。 */
interface ReaderSettingsRepository {
    val settings: StateFlow<ReaderSettings>

    fun updateGatewayMode(mode: ReaderGatewayMode)
    fun updateReaderBluetoothAddress(address: ReaderBluetoothAddress?)
}
