package jp.co.terumo.tracelink.rp902app.data.reader

import android.os.Build
import jp.co.terumo.tracelink.rp902app.data.reader.bluetooth.InMemoryReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902Gateway
import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902GatewayConfiguration
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionPreflight
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionPreflightState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEvent
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderPreflightFailure
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeStateRepository
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

/**
 * Settings に応じて active reader gateway を切り替える adapter。
 *
 * Repository からは 1 つの `ReaderGateway` として見えるが、内部では fake reader と
 * real RP902 reader を切り替える。real mode の場合は vendor SDK に進む前に app-owned な
 * preflight を確認し、接続できない理由を structured log に残す。
 */
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
    private val runtimeStateRepository: ReaderRuntimeStateRepository =
        InMemoryReaderRuntimeStateRepository(),
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 64)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    private val _events = MutableSharedFlow<ReaderGatewayEvent>(replay = 1, extraBufferCapacity = 64)
    override val events: Flow<ReaderGatewayEvent> = _events.asSharedFlow()

    private var currentSettings = settingsRepository.settings.value
    private var activeGatewayKey = settingsRepository.settings.value.toGatewayKey()
    private var activeGateway: ReaderGateway = createGateway(settingsRepository.settings.value)
    private var activeGatewayJob: Job? = collectActiveGateway(activeGateway)

    init {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Gateway mode: ${currentSettings.gatewayMode.name}",
        )

        settingsRepository.settings
            .drop(1)
            .onEach { settings ->
                currentSettings = settings
                val nextGatewayKey = settings.toGatewayKey()
                if (nextGatewayKey != activeGatewayKey) {
                    // mode または real reader の MAC が変わった場合は、古い gateway を停止して差し替える。
                    // 接続中の reader を残したまま差し替えると callback が古い経路へ残るため、必ず切断する。
                    switchGateway(settings, nextGatewayKey)
                } else {
                    emitEvent(
                        level = ReaderGatewayEventLevel.Info,
                        message = "Gateway settings updated: ${settings.gatewayMode.name}",
                    )
                }
            }
            .launchIn(scope)
    }

    override suspend fun connect() {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Connect requested. gatewayMode=${currentSettings.gatewayMode.name}",
        )
        if (!verifyRealPreflightIfNeeded()) {
            return
        }
        activeGateway.connect()
    }

    override suspend fun disconnect() {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Disconnect requested. gatewayMode=${currentSettings.gatewayMode.name}",
        )
        activeGateway.disconnect()
    }

    override suspend fun startInventory() {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Inventory start requested. gatewayMode=${currentSettings.gatewayMode.name}",
        )
        activeGateway.startInventory()
    }

    override suspend fun stopInventory() {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Inventory stop requested. gatewayMode=${currentSettings.gatewayMode.name}",
        )
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
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "Gateway mode: ${settings.gatewayMode.name}",
        )
    }

    private fun createGateway(settings: ReaderSettings): ReaderGateway =
        when (settings.gatewayMode) {
            ReaderGatewayMode.Fake -> fakeGatewayFactory()
            ReaderGatewayMode.RealRp902 -> realGatewayFactory(settings)
        }

    private fun collectActiveGateway(gateway: ReaderGateway): Job = scope.launch {
        // active gateway から来る状態・タグ・診断イベントを、この wrapper の Flow へ中継する。
        // Repository は mode 切替を意識せず同じ Flow を購読し続けられる。
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
        launch {
            gateway.events.collect { event ->
                _events.emit(event)
            }
        }
    }

    private fun verifyRealPreflightIfNeeded(): Boolean {
        val settings = currentSettings
        if (settings.gatewayMode != ReaderGatewayMode.RealRp902) {
            return true
        }

        // real RP902 では MAC、権限、Bluetooth 状態が不足していると vendor SDK 呼び出し前に止める。
        // 失敗理由をログに残すことで、実機で「なぜ接続できないか」を UI から追える。
        val runtimeState = runtimeStateRepository.runtimeState.value
        val preflight = ReaderConnectionPreflight.evaluate(
            settings = settings,
            sdkInt = sdkIntProvider(),
            grantedPermissions = runtimeState.grantedPermissions,
            bluetoothState = runtimeState.bluetoothState,
        )
        emitEvent(
            level = if (preflight.canAttemptConnection) {
                ReaderGatewayEventLevel.Info
            } else {
                ReaderGatewayEventLevel.Warning
            },
            message = preflight.toLogMessage(),
        )

        if (preflight.canAttemptConnection) {
            return true
        }

        val message = "RP902 preflight failed: ${preflight.failureReasonsText()}"
        _connectionState.value = ReaderConnectionState.Error(message)
        emitEvent(
            level = ReaderGatewayEventLevel.Error,
            message = message,
        )
        return false
    }

    private fun ReaderConnectionPreflightState.toLogMessage(): String =
        "Preflight result: " +
            if (canAttemptConnection) {
                "ready; bluetooth=${bluetoothState.name}; permissions=granted; address=configured"
            } else {
                "blocked; reasons=${failureReasonsText()}; " +
                    "missingPermissions=${missingPermissions.permissionsText()}; " +
                    "bluetooth=${bluetoothState.name}; " +
                    "addressConfigured=$hasBluetoothAddress"
            }

    private fun ReaderConnectionPreflightState.failureReasonsText(): String =
        failureReasons.joinToString(separator = "; ") { failure -> failure.displayText() }

    private fun List<ReaderRuntimePermission>.permissionsText(): String =
        if (isEmpty()) {
            "none"
        } else {
            joinToString(separator = ",") { permission -> permission.name }
        }

    private fun ReaderPreflightFailure.displayText(): String = when (this) {
        ReaderPreflightFailure.BluetoothAddressMissing -> "Bluetooth MAC address is not configured"
        ReaderPreflightFailure.RuntimePermissionsMissing -> "Required runtime permissions are missing"
        ReaderPreflightFailure.BluetoothStatusUnknown -> "Bluetooth status has not been checked"
        ReaderPreflightFailure.BluetoothDisabled -> "Bluetooth is disabled"
        ReaderPreflightFailure.BluetoothUnavailable -> "Bluetooth adapter is unavailable"
        ReaderPreflightFailure.BluetoothStatusPermissionMissing ->
            "Bluetooth status check is missing permission"
    }

    private fun emitEvent(
        level: ReaderGatewayEventLevel,
        message: String,
    ) {
        _events.tryEmit(
            ReaderGatewayEvent(
                level = level,
                message = message,
            ),
        )
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
