package jp.co.terumo.tracelink.rp902app.data.reader.real

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class RealRp902GatewayConfiguration(
    val bluetoothAddress: String? = null,
)

/**
 * Adapter boundary for future Unitech SDK wiring.
 *
 * Confirmed sample flow is TransportBluetooth(DeviceType.RP902, "RP902", mac)
 * -> RP902Reader(transport) -> addListener(...) -> connect(), with tag reads
 * arriving through IRfidUhfEventListener.onRfidUhfReadTag(...).
 *
 * TODO(real-rp902): add vendor imports only after Gradle dependency, runtime
 * permissions, Bluetooth address flow, and callback threading are decided.
 */
class RealRp902Gateway(
    private val configuration: RealRp902GatewayConfiguration = RealRp902GatewayConfiguration(),
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 64)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    override suspend fun connect() {
        _connectionState.value = ReaderConnectionState.Error(notWiredMessage("connect"))
    }

    override suspend fun disconnect() {
        _connectionState.value = ReaderConnectionState.Disconnected
    }

    override suspend fun startInventory() {
        _connectionState.value = ReaderConnectionState.Error(
            notWiredMessage("start inventory"),
        )
    }

    override suspend fun stopInventory() = Unit

    override fun close() {
        _connectionState.value = ReaderConnectionState.Disconnected
    }

    private fun notWiredMessage(action: String): String {
        val addressNote = if (configuration.bluetoothAddress.isNullOrBlank()) {
            " Bluetooth address is not configured."
        } else {
            ""
        }
        return "Real RP902 $action is not enabled in this build.$addressNote"
    }
}
