package jp.co.terumo.tracelink.rp902app.data.reader.real

import com.unitech.lib.reader.BaseReader
import com.unitech.lib.reader.event.IReaderEventListener
import com.unitech.lib.reader.types.KeyState
import com.unitech.lib.reader.types.KeyType
import com.unitech.lib.reader.types.NotificationState
import com.unitech.lib.rpx.RP902Reader
import com.unitech.lib.transport.TransportBluetooth
import com.unitech.lib.transport.types.ConnectState
import com.unitech.lib.types.ActionState
import com.unitech.lib.types.DeviceType
import com.unitech.lib.types.ResultCode
import com.unitech.lib.uhf.BaseUHF
import com.unitech.lib.uhf.event.IRfidUhfEventListener
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
 * TODO(real-rp902): add runtime permission requests, Bluetooth enablement checks,
 * inventory tuning, and callback threading verification after hardware testing.
 */
class RealRp902Gateway(
    private val configuration: RealRp902GatewayConfiguration = RealRp902GatewayConfiguration(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 64)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    private var reader: RP902Reader? = null

    private val readerEventListener = object : IReaderEventListener {
        override fun onReaderActionChanged(
            reader: BaseReader,
            retCode: ResultCode,
            state: ActionState,
            params: Any?,
        ) {
            if (retCode != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 action $state failed: ${retCode.toReadableMessage()}",
                )
            }
        }

        override fun onReaderBatteryState(
            reader: BaseReader,
            batteryState: Int,
            params: Any?,
        ) = Unit

        override fun onReaderKeyChanged(
            reader: BaseReader,
            type: KeyType,
            state: KeyState,
            params: Any?,
        ) = Unit

        override fun onReaderStateChanged(
            reader: BaseReader,
            state: ConnectState,
            params: Any?,
        ) {
            _connectionState.value = state.toDomainState()
            if (state == ConnectState.Connected) {
                reader.getRfidUhf()?.addListener(uhfEventListener)
            }
        }

        override fun onNotificationState(
            state: NotificationState,
            params: Any?,
        ) = Unit

        override fun onReaderTemperatureState(
            reader: BaseReader,
            temperatureState: Double,
            params: Any?,
        ) = Unit
    }

    private val uhfEventListener = object : IRfidUhfEventListener {
        override fun onRfidUhfAccessResult(
            uhf: BaseUHF,
            retCode: ResultCode,
            action: ActionState,
            epc: String?,
            data: String?,
            params: Any?,
        ) {
            if (retCode != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 UHF action $action failed: ${retCode.toReadableMessage()}",
                )
            }
        }

        override fun onRfidUhfReadTag(
            uhf: BaseUHF,
            tag: String?,
            params: Any?,
        ) {
            val epc = tag?.trim().orEmpty()
            if (epc.isNotBlank()) {
                _tagReads.tryEmit(
                    ReaderTagRead(
                        epc = epc,
                        seenAtEpochMillis = clock(),
                    ),
                )
            }
        }
    }

    override suspend fun connect() {
        val bluetoothAddress = configuration.bluetoothAddress?.trim().orEmpty()
        if (bluetoothAddress.isBlank()) {
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 Bluetooth address is not configured.",
            )
            return
        }

        runCatching {
            _connectionState.value = ReaderConnectionState.Connecting
            val transport = TransportBluetooth(
                DeviceType.RP902,
                RP902_DEVICE_NAME,
                bluetoothAddress,
            )
            val nextReader = RP902Reader(transport)
            nextReader.addListener(readerEventListener)
            reader = nextReader
            nextReader.connect()
        }.onFailure { throwable ->
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 connect failed: ${throwable.message.orEmpty()}",
            )
        }
    }

    override suspend fun disconnect() {
        runCatching { stopInventory() }
        reader?.getRfidUhf()?.removeListener(uhfEventListener)
        reader?.clearListener()
        reader?.disconnect()
        reader = null
        _connectionState.value = ReaderConnectionState.Disconnected
    }

    override suspend fun startInventory() {
        val activeReader = reader
        if (activeReader == null || _connectionState.value != ReaderConnectionState.Connected) {
            _connectionState.value = ReaderConnectionState.Error(
                "Connect RP902 before starting inventory.",
            )
            return
        }

        val uhf = activeReader.getRfidUhf()
        if (uhf == null) {
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 UHF module is not ready.",
            )
            return
        }

        runCatching {
            uhf.addListener(uhfEventListener)
            val result = uhf.inventory6c()
            if (result != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 inventory failed: ${result.toReadableMessage()}",
                )
            }
        }.onFailure { throwable ->
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 inventory failed: ${throwable.message.orEmpty()}",
            )
        }
    }

    override suspend fun stopInventory() {
        runCatching {
            val result = reader?.getRfidUhf()?.stop()
            if (result != null && result != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 stop inventory failed: ${result.toReadableMessage()}",
                )
            }
        }.onFailure { throwable ->
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 stop inventory failed: ${throwable.message.orEmpty()}",
            )
        }
    }

    override fun close() {
        reader?.getRfidUhf()?.removeListener(uhfEventListener)
        reader?.clearListener()
        reader?.disconnect()
        reader?.destroy()
        reader = null
        _connectionState.value = ReaderConnectionState.Disconnected
    }

    private fun ConnectState.toDomainState(): ReaderConnectionState = when (this) {
        ConnectState.Connected -> ReaderConnectionState.Connected
        ConnectState.Connecting -> ReaderConnectionState.Connecting
        ConnectState.Disconnected,
        ConnectState.Listen,
        -> ReaderConnectionState.Disconnected
    }

    private fun ResultCode.toReadableMessage(): String {
        val message = runCatching { getMessage() }.getOrNull()
        return if (message.isNullOrBlank()) toString() else message
    }

    companion object {
        private const val RP902_DEVICE_NAME = "RP902"
    }
}
