package jp.co.terumo.tracelink.rp902app.data.inventory

import java.util.UUID
import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.reader.FakeReaderGateway
import jp.co.terumo.tracelink.rp902app.data.readresult.FakeReadResultRepository
import jp.co.terumo.tracelink.rp902app.data.readresult.InMemoryPendingWriteQueue
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepositoryState
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventorySession
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.log.EventLogStore
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.reader.displayText
import jp.co.terumo.tracelink.rp902app.domain.readresult.PendingWriteQueue
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Inventory 機能の orchestration 実装。
 *
 * reader からの tag read、session 内重複除去、結果登録、pending write、structured log を
 * ここで組み合わせる。vendor SDK の詳細は `ReaderGateway` の内側に、PostgreSQL function
 * の詳細は `ReadResultRepository` の内側に閉じ込めることで、UI は repository state だけを見る。
 */
class DefaultInventoryRepository(
    private val readerGateway: ReaderGateway = FakeReaderGateway(),
    private val readResultRepository: ReadResultRepository = FakeReadResultRepository(),
    private val pendingWriteQueue: PendingWriteQueue = InMemoryPendingWriteQueue(),
    private val eventLogStore: EventLogStore = InMemoryEventLogStore(),
    private val inventorySession: InventorySession = InventorySession(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InventoryRepository {
    private val _state = MutableStateFlow(
        InventoryRepositoryState(
            logs = eventLogStore.logs.value,
            pendingWrites = pendingWriteQueue.pendingWrites.value,
        ),
    )
    override val state: StateFlow<InventoryRepositoryState> = _state.asStateFlow()

    init {
        // 複数の lower layer Flow を repository state に集約し、UI の source of truth を一本化する。
        eventLogStore.logs
            .onEach { logs ->
                _state.update { current -> current.copy(logs = logs) }
            }
            .launchIn(scope)

        pendingWriteQueue.pendingWrites
            .onEach { pendingWrites ->
                _state.update { current -> current.copy(pendingWrites = pendingWrites) }
            }
            .launchIn(scope)

        readerGateway.connectionState
            .onEach { connectionState ->
                _state.update { current ->
                    current.copy(
                        connectionState = connectionState,
                        // 接続断時に画面だけ Running のまま残らないよう、repository state で止める。
                        isInventoryRunning = current.isInventoryRunning &&
                            connectionState == ReaderConnectionState.Connected,
                    )
                }
                appendLog(
                    level = if (connectionState is ReaderConnectionState.Error) {
                        AppLogLevel.Error
                    } else {
                        AppLogLevel.Info
                    },
                    category = AppLogCategory.Reader,
                    message = "Reader: ${connectionState.displayText()}",
                )
            }
            .launchIn(scope)

        readerGateway.tagReads
            // tag read は gateway から非同期に流れるため、重複除去と state 更新を repository に閉じ込める。
            .onEach(::recordRead)
            .catch { throwable ->
                appendLog(
                    level = AppLogLevel.Error,
                    category = AppLogCategory.Inventory,
                    message = "Reader tag stream failed before repository update: ${throwable.message.orEmpty()}",
                )
            }
            .launchIn(scope)

        readerGateway.events
            .onEach { event ->
                appendLog(
                    level = event.level.toAppLogLevel(),
                    category = AppLogCategory.Reader,
                    message = event.message,
                )
            }
            .catch { throwable ->
                appendLog(
                    level = AppLogLevel.Error,
                    category = AppLogCategory.Reader,
                    message = "Reader diagnostic stream failed: ${throwable.message.orEmpty()}",
                )
            }
            .launchIn(scope)

        scope.launch {
            appendLog(
                level = AppLogLevel.Info,
                category = AppLogCategory.System,
                message = "Session initialized.",
            )
        }
    }

    override suspend fun connect() {
        runReaderCommand("Connect failed.") {
            readerGateway.connect()
        }
    }

    override suspend fun disconnect() {
        runReaderCommand("Disconnect failed.") {
            readerGateway.disconnect()
            _state.update { current -> current.copy(isInventoryRunning = false) }
            appendLog(
                level = AppLogLevel.Info,
                category = AppLogCategory.Inventory,
                message = "Inventory stopped by disconnect.",
            )
        }
    }

    override suspend fun startInventory() {
        runReaderCommand("Start inventory failed.") {
            readerGateway.startInventory()
            if (readerGateway.connectionState.value == ReaderConnectionState.Connected) {
                _state.update { current -> current.copy(isInventoryRunning = true) }
                appendLog(
                    level = AppLogLevel.Info,
                    category = AppLogCategory.Inventory,
                    message = "Inventory started.",
                )
            }
        }
    }

    override suspend fun stopInventory() {
        runReaderCommand("Stop inventory failed.") {
            readerGateway.stopInventory()
            _state.update { current -> current.copy(isInventoryRunning = false) }
            appendLog(
                level = AppLogLevel.Info,
                category = AppLogCategory.Inventory,
                message = "Inventory stopped.",
            )
        }
    }

    override suspend fun clearSession() {
        inventorySession.clear()
        _state.update { current ->
            current.copy(
                tags = emptyList(),
                registrationState = RegistrationState.Idle,
            )
        }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.Inventory,
            message = "Session cleared.",
        )
    }

    override suspend fun registerCurrentSessionResults() {
        val bundle = inventorySession.toRegistrationBundle(
            sessionId = sessionIdFactory(),
            registeredAtEpochMillis = clock(),
            deviceId = "android-local-device",
            readerType = "RP902",
        )

        if (bundle.tags.isEmpty()) {
            val message = "No tags to register."
            _state.update { current -> current.copy(registrationState = RegistrationState.Failed(message)) }
            appendLog(
                level = AppLogLevel.Warning,
                category = AppLogCategory.ResultRegistration,
                message = message,
            )
            return
        }

        _state.update { current -> current.copy(registrationState = RegistrationState.Registering) }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.ResultRegistration,
            message = "Result registration started with ${bundle.tags.size} tags.",
        )

        handleRegistrationResult(
            bundle = bundle,
            result = register(bundle),
            queueOnFailure = true,
        )
    }

    override suspend fun retryPendingWrites() {
        val pendingWrites = pendingWriteQueue.pendingWrites.value
        if (pendingWrites.isEmpty()) {
            appendLog(
                level = AppLogLevel.Info,
                category = AppLogCategory.ResultRegistration,
                message = "No pending writes to retry.",
            )
            return
        }

        _state.update { current -> current.copy(registrationState = RegistrationState.Registering) }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.ResultRegistration,
            message = "Retry started for ${pendingWrites.size} pending writes.",
        )

        var lastCompletedSessionId: String? = null
        var lastFailureMessage: String? = null

        pendingWrites.forEach { pendingWrite ->
            // 失敗が残っても他の pending write は続けて試し、現場復旧時の手戻りを減らす。
            val result = register(pendingWrite.bundle)
            when (result) {
                ReadResultRegistrationResult.Success -> {
                    pendingWriteQueue.remove(pendingWrite.id)
                    lastCompletedSessionId = pendingWrite.bundle.sessionId
                    appendLog(
                        level = AppLogLevel.Info,
                        category = AppLogCategory.ResultRegistration,
                        message = "Pending write completed: ${pendingWrite.bundle.sessionId}",
                    )
                }

                is ReadResultRegistrationResult.Failure -> {
                    lastFailureMessage = result.message
                    pendingWriteQueue.markAttemptFailed(
                        pendingWriteId = pendingWrite.id,
                        failedAtEpochMillis = clock(),
                        message = result.message,
                    )
                    appendLog(
                        level = AppLogLevel.Warning,
                        category = AppLogCategory.ResultRegistration,
                        message = "Pending write failed: ${result.message}",
                    )
                }
            }
        }

        _state.update { current ->
            current.copy(
                registrationState = lastFailureMessage?.let(RegistrationState::Failed)
                    ?: RegistrationState.Completed(lastCompletedSessionId.orEmpty()),
            )
        }
    }

    override fun close() {
        readerGateway.close()
        scope.cancel()
    }

    private suspend fun recordRead(read: ReaderTagRead) {
        // callback 由来の想定外値でアプリ全体を落とさないよう、ログ付きで安全に失敗させる。
        runCatching { inventorySession.record(read) }
            .onSuccess { tags ->
                _state.update { current -> current.copy(tags = tags) }
                val recordedTag = tags.firstOrNull { tag -> tag.epc == read.epc }
                appendLog(
                    level = AppLogLevel.Info,
                    category = AppLogCategory.Inventory,
                    message = "Read EPC ${read.epc}; " +
                        "repositoryAccepted=true uniqueTags=${tags.size} " +
                        "readCount=${recordedTag?.readCount ?: "unknown"}",
                )
            }
            .onFailure { throwable ->
                appendLog(
                    level = AppLogLevel.Warning,
                    category = AppLogCategory.Inventory,
                    message = "Read ignored: ${throwable.message.orEmpty()}",
                )
            }
    }

    private suspend fun handleRegistrationResult(
        bundle: ReadResultRegistrationBundle,
        result: ReadResultRegistrationResult,
        queueOnFailure: Boolean,
    ) {
        when (result) {
            ReadResultRegistrationResult.Success -> {
                pendingWriteQueue.remove(bundle.sessionId)
                _state.update { current ->
                    current.copy(registrationState = RegistrationState.Completed(bundle.sessionId))
                }
                appendLog(
                    level = AppLogLevel.Info,
                    category = AppLogCategory.ResultRegistration,
                    message = "Result registration completed.",
                )
            }

            is ReadResultRegistrationResult.Failure -> {
                if (queueOnFailure) {
                    pendingWriteQueue.enqueueFailure(
                        bundle = bundle,
                        failedAtEpochMillis = clock(),
                        message = result.message,
                    )
                }
                _state.update { current ->
                    current.copy(registrationState = RegistrationState.Failed(result.message))
                }
                appendLog(
                    level = AppLogLevel.Warning,
                    category = AppLogCategory.ResultRegistration,
                    message = "Result registration failed and queued: ${result.message}",
                )
            }
        }
    }

    private suspend fun register(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult =
        runCatching { readResultRepository.register(bundle) }
            .getOrElse { throwable ->
                ReadResultRegistrationResult.Failure(
                    throwable.message ?: "Unknown result registration error.",
                )
            }

    private suspend fun runReaderCommand(
        errorPrefix: String,
        command: suspend () -> Unit,
    ) {
        runCatching { command() }
            .onFailure { throwable ->
                appendLog(
                    level = AppLogLevel.Error,
                    category = AppLogCategory.Reader,
                    message = "$errorPrefix ${throwable.message.orEmpty()}",
                )
            }
    }

    private suspend fun appendLog(
        level: AppLogLevel,
        category: AppLogCategory,
        message: String,
    ) {
        eventLogStore.append(
            occurredAtEpochMillis = clock(),
            level = level,
            category = category,
            message = message,
        )
    }

    private fun ReaderGatewayEventLevel.toAppLogLevel(): AppLogLevel = when (this) {
        ReaderGatewayEventLevel.Info -> AppLogLevel.Info
        ReaderGatewayEventLevel.Warning -> AppLogLevel.Warning
        ReaderGatewayEventLevel.Error -> AppLogLevel.Error
    }
}
