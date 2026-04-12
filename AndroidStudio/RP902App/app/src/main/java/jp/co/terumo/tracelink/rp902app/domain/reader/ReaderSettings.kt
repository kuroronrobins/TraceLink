package jp.co.terumo.tracelink.rp902app.domain.reader

import kotlinx.coroutines.flow.StateFlow

data class ReaderSettings(
    val gatewayMode: ReaderGatewayMode = ReaderGatewayMode.Fake,
    val readerBluetoothAddress: ReaderBluetoothAddress? = null,
)

interface ReaderSettingsRepository {
    val settings: StateFlow<ReaderSettings>

    fun updateGatewayMode(mode: ReaderGatewayMode)
    fun updateReaderBluetoothAddress(address: ReaderBluetoothAddress?)
}
