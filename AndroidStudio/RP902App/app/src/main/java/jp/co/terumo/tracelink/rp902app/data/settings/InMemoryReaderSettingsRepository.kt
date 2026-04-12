package jp.co.terumo.tracelink.rp902app.data.settings

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothAddress
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * reader settings の in-memory 実装。
 *
 * 現在はアプリ起動中だけ保持する。将来、設定を端末に保存する場合は
 * `ReaderSettingsRepository` の契約を保ったまま永続化実装に差し替える。
 */
class InMemoryReaderSettingsRepository(
    initialSettings: ReaderSettings = ReaderSettings(),
) : ReaderSettingsRepository {
    private val _settings = MutableStateFlow(initialSettings)
    override val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    override fun updateGatewayMode(mode: ReaderGatewayMode) {
        _settings.update { current -> current.copy(gatewayMode = mode) }
    }

    override fun updateReaderBluetoothAddress(address: ReaderBluetoothAddress?) {
        _settings.update { current -> current.copy(readerBluetoothAddress = address) }
    }
}
