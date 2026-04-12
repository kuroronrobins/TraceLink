package jp.co.terumo.tracelink.rp902app.data.reader.real

import com.unitech.lib.reader.BaseReader
import com.unitech.lib.reader.event.IReaderEventListener
import com.unitech.lib.reader.params.DisplayOutput
import com.unitech.lib.reader.params.DisplayTags
import com.unitech.lib.reader.types.KeyState
import com.unitech.lib.reader.types.KeyType
import com.unitech.lib.reader.types.NotificationState
import com.unitech.lib.rpx.RP902Reader
import com.unitech.lib.transport.TransportBluetooth
import com.unitech.lib.transport.types.ConnectState
import com.unitech.lib.types.ActionState
import com.unitech.lib.types.BeepAndVibrateState
import com.unitech.lib.types.DeviceType
import com.unitech.lib.types.ReadOnceState
import com.unitech.lib.types.ResultCode
import com.unitech.lib.uhf.BaseUHF
import com.unitech.lib.uhf.event.IRfidUhfEventListener
import com.unitech.lib.uhf.params.TagExtParam
import com.unitech.lib.uhf.types.AlgorithmType
import com.unitech.lib.uhf.types.BLFType
import com.unitech.lib.uhf.types.Session
import com.unitech.lib.uhf.types.TARIType
import com.unitech.lib.uhf.types.Target
import java.util.concurrent.atomic.AtomicLong
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEvent
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RealRp902GatewayConfiguration(
    val bluetoothAddress: String? = null,
)

/**
 * Unitech RP902 SDK を app-owned な `ReaderGateway` へ変換する adapter。
 *
 * 確認済みの基本経路は
 * `TransportBluetooth(DeviceType.RP902, "RP902", mac)` ->
 * `RP902Reader(transport)` -> `addListener(...)` -> `connect()`。
 * タグ読取は `IRfidUhfEventListener.onRfidUhfReadTag(...)` から来る想定。
 *
 * この class は vendor SDK を直接触る唯一の場所なので、未確認 API を広げないこと。
 * callback thread、null payload、native crash を想定し、app 側の状態とログへ安全に変換する。
 */
class RealRp902Gateway(
    private val configuration: RealRp902GatewayConfiguration = RealRp902GatewayConfiguration(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 64)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    private val _events = MutableSharedFlow<ReaderGatewayEvent>(
        replay = 1,
        extraBufferCapacity = 256,
    )
    override val events: Flow<ReaderGatewayEvent> = _events.asSharedFlow()

    @Volatile
    private var reader: RP902Reader? = null

    @Volatile
    private var isUhfListenerAttached = false

    @Volatile
    private var attachedUhf: BaseUHF? = null

    @Volatile
    private var attachedUhfHash: Int? = null

    @Volatile
    private var activeInventoryUhf: BaseUHF? = null

    @Volatile
    private var inventoryStartedAtEpochMillis: Long? = null

    @Volatile
    private var lastTagCallbackAtEpochMillis: Long? = null

    private val commandMutex = Mutex()
    private val inventoryAttemptCount = AtomicLong(0L)
    private val tagCallbackCount = AtomicLong(0L)
    private val tagModelSuccessCount = AtomicLong(0L)
    private val tagModelFailureCount = AtomicLong(0L)
    private val suppressedDisplayOutputCount = AtomicLong(0L)
    private val listenerAttachCount = AtomicLong(0L)
    private val listenerDetachCount = AtomicLong(0L)

    // TODO(real-rp902): 実機で callback thread の保証を確認する。
    // 現状は callback 内で例外を捕捉し、StateFlow/SharedFlow 更新に限定している。
    // UI 操作や重い処理をここへ追加しない。
    private val readerEventListener = object : IReaderEventListener {
        override fun onReaderActionChanged(
            reader: BaseReader?,
            retCode: ResultCode?,
            state: ActionState?,
            params: Any?,
        ) {
            runVendorCallback("onReaderActionChanged") {
                emitEvent(
                    level = if (retCode == ResultCode.NoError) {
                        ReaderGatewayEventLevel.Info
                    } else {
                        ReaderGatewayEventLevel.Error
                    },
                    message = "Vendor callback onReaderActionChanged reached: " +
                        "state=$state result=${retCode.toReadableMessage()} thread=${threadName()}",
                )
                if (retCode != ResultCode.NoError) {
                    _connectionState.value = ReaderConnectionState.Error(
                        "RP902 action $state failed: ${retCode.toReadableMessage()}",
                    )
                }
            }
        }

        override fun onReaderBatteryState(
            reader: BaseReader?,
            batteryState: Int,
            params: Any?,
        ) = Unit

        override fun onReaderKeyChanged(
            reader: BaseReader?,
            type: KeyType?,
            state: KeyState?,
            params: Any?,
        ) = Unit

        override fun onReaderStateChanged(
            reader: BaseReader?,
            state: ConnectState?,
            params: Any?,
        ) {
            runVendorCallback("onReaderStateChanged") {
                emitEvent(
                    level = ReaderGatewayEventLevel.Info,
                    message = "Vendor callback onReaderStateChanged reached: " +
                        "state=$state thread=${threadName()}",
                )
                _connectionState.value = state.toDomainState()
                if (state == ConnectState.Connected) {
                    val callbackUhf = reader?.getRfidUhf()
                    val gatewayUhf = this@RealRp902Gateway.reader?.getRfidUhf()
                    attachUhfListener(
                        uhf = callbackUhf ?: gatewayUhf,
                        source = "Connected callback " +
                            "callbackReaderHash=${reader.identityHashText()} " +
                            "gatewayReaderHash=${this@RealRp902Gateway.reader.identityHashText()} " +
                            "callbackUhfHash=${callbackUhf.identityHashText()} " +
                            "gatewayUhfHash=${gatewayUhf.identityHashText()}",
                    )
                }
            }
        }

        override fun onNotificationState(
            state: NotificationState?,
            params: Any?,
        ) = Unit

        override fun onReaderTemperatureState(
            reader: BaseReader?,
            temperatureState: Double,
            params: Any?,
        ) = Unit
    }

    private val uhfEventListener = object : IRfidUhfEventListener {
        override fun onRfidUhfAccessResult(
            uhf: BaseUHF?,
            retCode: ResultCode?,
            action: ActionState?,
            epc: String?,
            data: String?,
            params: Any?,
        ) {
            runVendorCallback("onRfidUhfAccessResult") {
                emitEvent(
                    level = if (retCode == ResultCode.NoError) {
                        ReaderGatewayEventLevel.Info
                    } else {
                        ReaderGatewayEventLevel.Error
                    },
                    message = "Vendor callback onRfidUhfAccessResult reached: " +
                        "action=$action result=${retCode.toReadableMessage()} " +
                        "epcPresent=${!epc.isNullOrBlank()} dataPresent=${!data.isNullOrBlank()} " +
                        "thread=${threadName()}",
                )
                if (retCode != ResultCode.NoError) {
                    _connectionState.value = ReaderConnectionState.Error(
                        "RP902 UHF action $action failed: ${retCode.toReadableMessage()}",
                    )
                }
            }
        }

        override fun onRfidUhfReadTag(
            uhf: BaseUHF?,
            tag: String?,
            params: Any?,
        ) {
            // vendor callback はタグを近づけた瞬間に SDK 側 thread から呼ばれる。
            // payload が想定外でも app 全体を落とさないよう、mapper とログで安全に扱う。
            runVendorCallback("onRfidUhfReadTag") {
                val callbackIndex = tagCallbackCount.incrementAndGet()
                val now = clock()
                lastTagCallbackAtEpochMillis = now
                val mapping = Rp902TagEventMapper.map(
                    rawTag = tag,
                    params = params,
                    seenAtEpochMillis = now,
                    callbackIndex = callbackIndex,
                )

                if (callbackIndex == 1L) {
                    emitEvent(
                        level = ReaderGatewayEventLevel.Info,
                        message = "First RP902 tag callback reached: " +
                            "${rawTagPayloadDiagnostic(tag, params, uhf)} thread=${threadName()}",
                    )
                }

                if (shouldLogTagDiagnostic(callbackIndex)) {
                    emitEvent(
                        level = ReaderGatewayEventLevel.Info,
                        message = "${mapping.diagnosticMessage} " +
                            "${rawTagPayloadDiagnostic(tag, params, uhf)} thread=${threadName()}",
                    )
                }

                val failureMessage = mapping.failureMessage
                val read = mapping.read
                if (failureMessage != null || read == null) {
                    val failures = tagModelFailureCount.incrementAndGet()
                    emitEvent(
                        level = ReaderGatewayEventLevel.Warning,
                        message = "$failureMessage failures=$failures",
                    )
                    return@runVendorCallback
                }

                if (_tagReads.tryEmit(read)) {
                    val successes = tagModelSuccessCount.incrementAndGet()
                    if (shouldLogTagDiagnostic(callbackIndex)) {
                        emitEvent(
                            level = ReaderGatewayEventLevel.Info,
                            message = "RP902 tag model conversion succeeded: " +
                                "successes=$successes epcLength=${read.epc.length}",
                        )
                    }
                } else {
                    val failures = tagModelFailureCount.incrementAndGet()
                    emitEvent(
                        level = ReaderGatewayEventLevel.Warning,
                        message = "RP902 tag event dropped before repository: " +
                            "tagReads buffer is full; failures=$failures",
                    )
                }
            }
        }
    }

    override suspend fun connect(): Unit = runReaderCommandOnCommandDispatcher {
        val bluetoothAddress = configuration.bluetoothAddress?.trim().orEmpty()
        if (bluetoothAddress.isBlank()) {
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 connect blocked: Bluetooth address is not configured.",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 Bluetooth address is not configured.",
            )
            return@runReaderCommandOnCommandDispatcher
        }

        runCatching {
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 connect started. " +
                    "mac=${bluetoothAddress.maskMacAddress()} thread=${threadName()}",
            )
            _connectionState.value = ReaderConnectionState.Connecting
            val transport = TransportBluetooth(
                DeviceType.RP902,
                RP902_DEVICE_NAME,
                bluetoothAddress,
            )
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "TransportBluetooth created for RP902. " +
                    "DisplayOutput calls will be suppressed before vendor JNI.",
            )
            val nextReader = DisplayOutputSuppressingRp902Reader(
                transport = transport,
                onDisplayOutputSuppressed = ::onDisplayOutputSuppressed,
            )
            // reader state callback は接続成功/失敗を domain state に変換するための入口。
            // UHF listener は Connected callback と startInventory の両方で確認し、漏れをログで追う。
            nextReader.addListener(readerEventListener)
            reader = nextReader
            nextReader.connect()
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 connect() invoked; waiting for vendor state callback.",
            )
        }.onFailure { throwable ->
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 connect failed before state callback: ${throwable.readableMessage()}",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 connect failed: ${throwable.message.orEmpty()}",
            )
        }
    }

    override suspend fun disconnect(): Unit = runReaderCommandOnCommandDispatcher {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 disconnect requested. thread=${threadName()}",
        )
        runCatching { stopInventoryOnCommandThread(source = "disconnect") }
        detachUhfListener(source = "disconnect")
        reader?.clearListener()
        reader?.disconnect()
        reader = null
        _connectionState.value = ReaderConnectionState.Disconnected
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 disconnect completed.",
        )
    }

    override suspend fun startInventory(): Unit = runReaderCommandOnCommandDispatcher {
        val attemptIndex = inventoryAttemptCount.incrementAndGet()
        val requestedAt = clock()
        resetInventoryDiagnostics(startedAtEpochMillis = requestedAt)
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 inventory start requested: " +
                "attempt=$attemptIndex connected=${_connectionState.value == ReaderConnectionState.Connected} " +
                "${listenerDiagnostic()} thread=${threadName()}",
        )
        val activeReader = reader
        if (activeReader == null || _connectionState.value != ReaderConnectionState.Connected) {
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 inventory start blocked: reader is not connected.",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "Connect RP902 before starting inventory.",
            )
            return@runReaderCommandOnCommandDispatcher
        }

        val uhf = activeReader.getRfidUhf()
        activeInventoryUhf = uhf
        if (uhf == null) {
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 inventory start blocked: UHF module is not ready.",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 UHF module is not ready.",
            )
            return@runReaderCommandOnCommandDispatcher
        }

        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 inventory listener preflight: " +
                "${listenerDiagnostic(activeUhf = uhf)} readerHash=${activeReader.identityHashText()} " +
                "thread=${threadName()}",
        )

        runCatching {
            // 実機ログで UHF instance が connect 時と start 時で異なる可能性を観測できるよう、
            // listener identity と UHF object hash をログに残している。
            if (!attachUhfListener(uhf = uhf, source = "startInventory")) {
                return@runCatching
            }
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 inventory listener ready: " +
                    "${listenerDiagnostic(activeUhf = uhf)} thread=${threadName()}",
            )
            configureSampleBackedInventoryTuning(uhf)
            clearSampleSelectMasks(uhf)
            configureMinimalInventoryDisplayPath(activeReader)
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "Calling inventory6c(); " +
                    "RP902 DisplayOutput JNI path is suppressed. thread=${threadName()}",
            )
            val result = uhf.inventory6c()
            val durationMs = clock() - requestedAt
            emitEvent(
                level = if (result == ResultCode.NoError) {
                    ReaderGatewayEventLevel.Info
                } else {
                    ReaderGatewayEventLevel.Error
                },
                message = "RP902 inventory6c() returned ${result.toReadableMessage()}: " +
                    "attempt=$attemptIndex durationMs=$durationMs " +
                    "callbacks=${tagCallbackCount.get()} acceptedTags=${tagModelSuccessCount.get()} " +
                    "thread=${threadName()}",
            )
            if (result != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 inventory failed: ${result.toReadableMessage()}",
                )
            }
        }.onFailure { throwable ->
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 inventory start threw: ${throwable.readableMessage()}",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 inventory failed: ${throwable.message.orEmpty()}",
            )
        }
    }

    override suspend fun stopInventory(): Unit = runReaderCommandOnCommandDispatcher {
        stopInventoryOnCommandThread(source = "manual")
    }

    private fun stopInventoryOnCommandThread(source: String) {
        val stoppedAt = clock()
        val runningDurationMs = inventoryStartedAtEpochMillis?.let { startedAt ->
            stoppedAt - startedAt
        }
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
                message = "RP902 inventory stop requested: " +
                "source=$source callbacks=${tagCallbackCount.get()} " +
                "acceptedTags=${tagModelSuccessCount.get()} " +
                "runningDurationMs=${runningDurationMs ?: "unknown"} " +
                "lastTagAt=${lastTagCallbackAtEpochMillis ?: "none"} " +
                "${listenerDiagnostic(activeUhf = activeInventoryUhf)} " +
                "thread=${threadName()}",
        )
        runCatching {
            val stopUhf = activeInventoryUhf ?: reader?.getRfidUhf()
            val result = stopUhf?.stop()
            emitEvent(
                level = if (result == null || result == ResultCode.NoError) {
                    ReaderGatewayEventLevel.Info
                } else {
                    ReaderGatewayEventLevel.Error
                },
                message = "RP902 stop() returned ${result?.toReadableMessage() ?: "not connected"}: " +
                    "source=$source callbacks=${tagCallbackCount.get()} " +
                    "acceptedTags=${tagModelSuccessCount.get()} " +
                    "mappingFailures=${tagModelFailureCount.get()} " +
                    "runningDurationMs=${runningDurationMs ?: "unknown"} " +
                    "lastTagAt=${lastTagCallbackAtEpochMillis ?: "none"} " +
                    "stopUhfHash=${stopUhf.identityHashText()} " +
                    "${listenerDiagnostic(activeUhf = stopUhf)} " +
                    "thread=${threadName()}",
            )
            if (result != null && result != ResultCode.NoError) {
                _connectionState.value = ReaderConnectionState.Error(
                    "RP902 stop inventory failed: ${result.toReadableMessage()}",
                )
            }
        }.onFailure { throwable ->
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 inventory stop threw: ${throwable.readableMessage()}",
            )
            _connectionState.value = ReaderConnectionState.Error(
                "RP902 stop inventory failed: ${throwable.message.orEmpty()}",
            )
        }.onSuccess {
            activeInventoryUhf = null
        }
    }

    override fun close() {
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 gateway close requested.",
        )
        detachUhfListener(source = "close")
        reader?.clearListener()
        reader?.disconnect()
        reader?.destroy()
        reader = null
        _connectionState.value = ReaderConnectionState.Disconnected
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 gateway closed.",
        )
    }

    private fun ConnectState?.toDomainState(): ReaderConnectionState = when (this) {
        ConnectState.Connected -> ReaderConnectionState.Connected
        ConnectState.Connecting -> ReaderConnectionState.Connecting
        ConnectState.Disconnected,
        ConnectState.Listen,
        null,
        -> ReaderConnectionState.Disconnected
    }

    private fun ResultCode?.toReadableMessage(): String {
        if (this == null) return "null"
        val message = runCatching { getMessage() }.getOrNull()
        return if (message.isNullOrBlank()) toString() else message
    }

    private fun emitEvent(
        level: ReaderGatewayEventLevel,
        message: String,
    ) {
        runCatching {
            _events.tryEmit(
                ReaderGatewayEvent(
                    level = level,
                    message = message,
                ),
            )
        }
    }

    private fun runVendorCallback(
        callbackName: String,
        block: () -> Unit,
    ) {
        // vendor callback thread で例外が外へ漏れるとアプリが落ちる可能性がある。
        // ここで必ず捕捉し、ReaderConnectionState.Error と structured log に変換する。
        runCatching(block)
            .onFailure { throwable ->
                val message = "RP902 vendor callback $callbackName failed: ${throwable.readableMessage()}"
                emitEvent(
                    level = ReaderGatewayEventLevel.Error,
                    message = "$message thread=${threadName()}",
                )
                _connectionState.value = ReaderConnectionState.Error(message)
            }
    }

    private fun attachUhfListener(
        uhf: BaseUHF?,
        source: String,
    ): Boolean {
        if (uhf == null) {
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = "RP902 UHF listener attach skipped from $source: " +
                    "UHF module is null. ${listenerDiagnostic()}",
            )
            return false
        }

        val currentAttachedUhf = attachedUhf
        val uhfHash = System.identityHashCode(uhf)
        if (isUhfListenerAttached && currentAttachedUhf === uhf) {
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 UHF listener already attached; " +
                    "source=$source ${listenerDiagnostic(activeUhf = uhf)}",
            )
            return true
        }

        if (isUhfListenerAttached && currentAttachedUhf != null) {
            // 古い UHF instance に listener が残ると、Start しても callback が届かない原因になる。
            // active inventory の UHF instance に付け直し、hash をログで比較できるようにする。
            emitEvent(
                level = ReaderGatewayEventLevel.Warning,
                message = "RP902 UHF listener attached to a different UHF instance; " +
                    "reattaching to inventory UHF. source=$source " +
                    "previousUhfHash=${attachedUhfHash ?: "none"} nextUhfHash=$uhfHash " +
                    "listenerHash=${listenerHash()}",
            )
            detachUhfListenerFrom(currentAttachedUhf, source = "reattach:$source")
        }

        return runCatching {
            uhf.removeListener(uhfEventListener)
            uhf.addListener(uhfEventListener)
            isUhfListenerAttached = true
            attachedUhf = uhf
            attachedUhfHash = uhfHash
            val attachIndex = listenerAttachCount.incrementAndGet()
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 UHF listener attached; " +
                    "source=$source attachCount=$attachIndex " +
                    "${listenerDiagnostic(activeUhf = uhf)}",
            )
            true
        }.getOrElse { throwable ->
            val message = "RP902 UHF listener attach failed from $source: ${throwable.readableMessage()}"
            emitEvent(
                level = ReaderGatewayEventLevel.Error,
                message = message,
            )
            _connectionState.value = ReaderConnectionState.Error(message)
            false
        }
    }

    private fun detachUhfListener(source: String) {
        if (!isUhfListenerAttached) {
            return
        }

        val uhf = attachedUhf ?: reader?.getRfidUhf()
        detachUhfListenerFrom(uhf, source = source)
    }

    private fun detachUhfListenerFrom(
        uhf: BaseUHF?,
        source: String,
    ) {
        runCatching {
            uhf?.removeListener(uhfEventListener)
        }.onFailure { throwable ->
            emitEvent(
                level = ReaderGatewayEventLevel.Warning,
                message = "RP902 UHF listener detach failed from $source: ${throwable.readableMessage()}",
            )
        }
        isUhfListenerAttached = false
        attachedUhf = null
        attachedUhfHash = null
        val detachIndex = listenerDetachCount.incrementAndGet()
        emitEvent(
            level = ReaderGatewayEventLevel.Info,
            message = "RP902 UHF listener detached; " +
                "source=$source detachCount=$detachIndex detachedUhfHash=${uhf.identityHashText()} " +
                "listenerHash=${listenerHash()}",
        )
    }

    private fun shouldLogTagDiagnostic(callbackIndex: Long): Boolean {
        return callbackIndex <= FirstTagDiagnosticsCount ||
            callbackIndex % TagDiagnosticInterval == 0L
    }

    private fun onDisplayOutputSuppressed(displayOutput: DisplayOutput?) {
        val suppressedIndex = suppressedDisplayOutputCount.incrementAndGet()
        if (suppressedIndex <= FirstDisplayOutputDiagnosticsCount ||
            suppressedIndex % DisplayOutputDiagnosticInterval == 0L
        ) {
            emitEvent(
                level = ReaderGatewayEventLevel.Warning,
                message = "RP902 DisplayOutput suppressed before vendor JNI: " +
                    "count=$suppressedIndex parameter=${displayOutput?.parameter} " +
                    "textLength=${displayOutput?.sdata?.length ?: 0} " +
                    "textPresent=${!displayOutput?.sdata.isNullOrBlank()} " +
                    "thread=${threadName()}",
            )
        }
    }

    private suspend fun runReaderCommandOnCommandDispatcher(
        block: suspend () -> Unit,
    ) {
        withContext(commandDispatcher) {
            commandMutex.withLock {
                block()
            }
        }
    }

    private fun resetInventoryDiagnostics(startedAtEpochMillis: Long) {
        inventoryStartedAtEpochMillis = startedAtEpochMillis
        lastTagCallbackAtEpochMillis = null
        tagCallbackCount.set(0L)
        tagModelSuccessCount.set(0L)
        tagModelFailureCount.set(0L)
    }

    private fun configureSampleBackedInventoryTuning(uhf: BaseUHF) {
        // vendor sample の `SampleFragment.initSetting()` で確認できた inventory 設定だけを適用する。
        // 失敗した設定があっても全体を落とさず、どの設定が失敗したかログに残して次の切り分けに使う。
        var applied = 0
        var failed = 0

        fun applySetting(name: String, block: () -> Unit) {
            runCatching(block)
                .onSuccess { applied++ }
                .onFailure { throwable ->
                    failed++
                    emitEvent(
                        level = ReaderGatewayEventLevel.Warning,
                        message = "RP902 sample-backed UHF tuning failed: " +
                            "$name ${throwable.readableMessage()} thread=${threadName()}",
                    )
                }
        }

        applySetting("setSession(S0)") { uhf.setSession(Session.S0) }
        applySetting("setContinuousMode(true)") { uhf.setContinuousMode(true) }
        applySetting("setInventoryTime(200)") { uhf.setInventoryTime(200) }
        applySetting("setIdleTime(20)") { uhf.setIdleTime(20) }
        applySetting("setAlgorithmType(DynamicQ)") { uhf.setAlgorithmType(AlgorithmType.DynamicQ) }
        applySetting("setStartQ(4)") { uhf.setStartQ(4) }
        applySetting("setMaxQ(15)") { uhf.setMaxQ(15) }
        applySetting("setMinQ(0)") { uhf.setMinQ(0) }
        applySetting("setTarget(A)") { uhf.setTarget(Target.A) }
        applySetting("setToggleTarget(true)") { uhf.setToggleTarget(true) }
        applySetting("setPower(22)") { uhf.setPower(RP902_SAMPLE_POWER) }
        applySetting("setTARI(T_25_00)") { uhf.setTARI(TARIType.T_25_00) }
        applySetting("setBLF(BLF_256)") { uhf.setBLF(BLFType.BLF_256) }
        applySetting("setFastMode(true)") { uhf.setFastMode(true) }

        emitEvent(
            level = if (failed == 0) ReaderGatewayEventLevel.Info else ReaderGatewayEventLevel.Warning,
            message = "RP902 sample-backed UHF tuning completed: " +
                "applied=$applied failed=$failed thread=${threadName()}",
        )
    }

    private fun clearSampleSelectMasks(uhf: BaseUHF) {
        // 過去の実機操作や sample app で select mask が残っていると、タグが読めない原因になる。
        // sample と同じ 2 slot だけを対象にし、未確認範囲へ API を広げない。
        var applied = 0
        var failed = 0
        repeat(SampleSelectMaskCount) { index ->
            runCatching {
                uhf.setSelectMask6cEnabled(index, false)
            }.onSuccess {
                applied++
            }.onFailure { throwable ->
                failed++
                emitEvent(
                    level = ReaderGatewayEventLevel.Warning,
                    message = "RP902 select mask clear failed: " +
                        "index=$index ${throwable.readableMessage()} thread=${threadName()}",
                )
            }
        }
        emitEvent(
            level = if (failed == 0) ReaderGatewayEventLevel.Info else ReaderGatewayEventLevel.Warning,
            message = "RP902 select mask clear completed: " +
                "applied=$applied failed=$failed thread=${threadName()}",
        )
    }

    private fun configureMinimalInventoryDisplayPath(activeReader: RP902Reader) {
        // アプリの画面 Start では RP902 本体の表示/ビープ/バイブを必須にしていない。
        // DisplayOutput JNI crash 経路を避けるため、確認済み API の範囲で最小表示設定に寄せる。
        runCatching {
            activeReader.setDisplayTags(
                DisplayTags(
                    ReadOnceState.Off,
                    BeepAndVibrateState.Off,
                ),
            )
        }.onSuccess {
            emitEvent(
                level = ReaderGatewayEventLevel.Info,
                message = "RP902 DisplayTags configured for minimal inventory: " +
                    "readOnce=Off beepVibrate=Off.",
            )
        }.onFailure { throwable ->
            emitEvent(
                level = ReaderGatewayEventLevel.Warning,
                message = "RP902 DisplayTags minimal configuration failed; " +
                    "continuing with DisplayOutput suppression: ${throwable.readableMessage()}",
            )
        }
    }

    private fun rawTagPayloadDiagnostic(
        rawTag: String?,
        params: Any?,
        callbackUhf: BaseUHF?,
    ): String {
        val tagExtParam = params as? TagExtParam
        val rssi = runCatching { tagExtParam?.getRssi() }.getOrNull()
        val tid = runCatching { tagExtParam?.getTID() }.getOrNull()
        return "rawPresent=${rawTag != null} " +
            "rawLength=${rawTag?.length ?: 0} " +
            "paramsType=${params?.javaClass?.name ?: "null"} " +
            "tagExtParam=${tagExtParam != null} " +
            "rssiPresent=${rssi != null} " +
            "tidPresent=${!tid.isNullOrBlank()} " +
            "callbackUhfHash=${callbackUhf.identityHashText()} " +
            "attachedUhfHash=${attachedUhfHash ?: "none"} " +
            "callbackMatchesAttached=${callbackUhf != null && callbackUhf === attachedUhf} " +
            "listenerHash=${listenerHash()}"
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf(String::isNotBlank) ?: this::class.java.simpleName

    private fun threadName(): String = Thread.currentThread().name

    private fun listenerDiagnostic(activeUhf: BaseUHF? = null): String =
        "listenerAttached=$isUhfListenerAttached " +
            "listenerHash=${listenerHash()} " +
            "attachedUhfHash=${attachedUhfHash ?: "none"} " +
            "activeUhfHash=${activeUhf.identityHashText()} " +
            "activeMatchesAttached=${activeUhf != null && activeUhf === attachedUhf} " +
            "attachCount=${listenerAttachCount.get()} " +
            "detachCount=${listenerDetachCount.get()} " +
            "callbackTotal=${tagCallbackCount.get()} " +
            "lastCallbackAt=${lastTagCallbackAtEpochMillis ?: "none"}"

    private fun listenerHash(): Int = System.identityHashCode(uhfEventListener)

    private fun Any?.identityHashText(): String =
        this?.let { System.identityHashCode(it).toString() } ?: "none"

    private fun String.maskMacAddress(): String =
        if (length >= 5) {
            "***${takeLast(5)}"
        } else {
            "***"
        }

    companion object {
        private const val RP902_DEVICE_NAME = "RP902"
        private const val FirstTagDiagnosticsCount = 5L
        private const val TagDiagnosticInterval = 100L
        private const val FirstDisplayOutputDiagnosticsCount = 5L
        private const val DisplayOutputDiagnosticInterval = 100L
        private const val RP902_SAMPLE_POWER = 22
        private const val SampleSelectMaskCount = 2
    }
}
