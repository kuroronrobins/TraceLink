package jp.co.terumo.tracelink.rp902app.data.reader

import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902Gateway
import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902GatewayConfiguration
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ConfigurableReaderGateway(
    settingsRepository: ReaderSettingsRepository,
    private val fakeGatewayFactory: () -> ReaderGateway = { FakeReaderGateway() },
    private val realGatewayFactory: (ReaderSettings) -> ReaderGateway = { settings ->
        RealRp902Gateway(
            RealRp902GatewayConfiguration(
                bluetoothAddress = settings.readerBluetoothAddress?.value,
            ),
        )
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 64)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    private var activeGatewayKey = settingsRepository.settings.value.toGatewayKey()
    private var activeGateway: ReaderGateway = createGateway(settingsRepository.settings.value)
    private var activeGatewayJob: Job? = collectActiveGateway(activeGateway)

    init {
        settingsRepository.settings
            .drop(1)
            .onEach { settings ->
                val nextGatewayKey = settings.toGatewayKey()
                if (nextGatewayKey != activeGatewayKey) {
                    switchGateway(settings, nextGatewayKey)
                }
            }
            .launchIn(scope)
    }

    override suspend fun connect() {
        activeGateway.connect()
    }

    override suspend fun disconnect() {
        activeGateway.disconnect()
    }

    override suspend fun startInventory() {
        activeGateway.startInventory()
    }

    override suspend fun stopInventory() {
        activeGateway.stopInventory()
    }

    override fun close() {
        activeGatewayJob?.cancel()
        activeGateway.close()
        scope.cancel()
    }

    private suspend fun switchGateway(
        settings: ReaderSettings,
        nextGatewayKey: GatewayKey,
    ) {
        val previousGateway = activeGateway
        runCatching { previousGateway.stopInventory() }
        runCatching { previousGateway.disconnect() }
        previousGateway.close()
        activeGatewayJob?.cancel()

        activeGatewayKey = nextGatewayKey
        activeGateway = createGateway(settings)
        activeGatewayJob = collectActiveGateway(activeGateway)
        _connectionState.value = activeGateway.connectionState.value
    }

    private fun createGateway(settings: ReaderSettings): ReaderGateway =
        when (settings.gatewayMode) {
            ReaderGatewayMode.Fake -> fakeGatewayFactory()
            ReaderGatewayMode.RealRp902 -> realGatewayFactory(settings)
        }

    private fun collectActiveGateway(gateway: ReaderGateway): Job = scope.launch {
        launch {
            gateway.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
        launch {
            gateway.tagReads.collect { read ->
                _tagReads.emit(read)
            }
        }
    }

    private fun ReaderSettings.toGatewayKey(): GatewayKey = GatewayKey(
        mode = gatewayMode,
        bluetoothAddress = if (gatewayMode == ReaderGatewayMode.RealRp902) {
            readerBluetoothAddress?.value
        } else {
            null
        },
    )

    private data class GatewayKey(
        val mode: ReaderGatewayMode,
        val bluetoothAddress: String?,
    )
}
