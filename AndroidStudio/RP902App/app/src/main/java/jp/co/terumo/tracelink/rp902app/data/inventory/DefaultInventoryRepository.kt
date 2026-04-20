package jp.co.terumo.tracelink.rp902app.data.inventory

import java.util.Locale
import java.util.UUID
import jp.co.terumo.tracelink.rp902app.data.equipment.FakeEquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.data.judgement.SimpleReadJudgementService
import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.reader.FakeReaderGateway
import jp.co.terumo.tracelink.rp902app.data.readresult.DefaultReadResultBundleFactory
import jp.co.terumo.tracelink.rp902app.data.readresult.FakeReadResultRepository
import jp.co.terumo.tracelink.rp902app.data.readresult.InMemoryPendingWriteQueue
import jp.co.terumo.tracelink.rp902app.data.rule.FakeRuleRepository
import jp.co.terumo.tracelink.rp902app.data.work.FakeWorkContextRepository
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepositoryState
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventorySession
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementService
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.log.EventLogStore
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.reader.displayText
import jp.co.terumo.tracelink.rp902app.domain.readresult.PendingWriteQueue
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationEnvironment
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultBundleFactory
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationState
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleRepository
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContextRepository
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
 * Inventory 画面向け repository の一時 facade 実装。
 *
 * UI 互換の入口を維持しつつ、work/rule/equipment 取得、判定、登録 bundle 生成、
 * 結果登録、pending write をそれぞれの契約へ委譲する。
 */
class DefaultInventoryRepository(
    private val readerGateway: ReaderGateway = FakeReaderGateway(),
    private val workContextRepository: WorkContextRepository = FakeWorkContextRepository(),
    private val ruleRepository: RuleRepository = FakeRuleRepository(),
    private val equipmentMasterRepository: EquipmentMasterRepository = FakeEquipmentMasterRepository(),
    private val readJudgementService: ReadJudgementService = SimpleReadJudgementService(),
    private val readResultBundleFactory: ReadResultBundleFactory = DefaultReadResultBundleFactory(),
    private val readResultRepository: ReadResultRepository = FakeReadResultRepository(),
    private val pendingWriteQueue: PendingWriteQueue = InMemoryPendingWriteQueue(),
    private val eventLogStore: EventLogStore = InMemoryEventLogStore(),
    private val inventorySession: InventorySession = InventorySession(),
    private val registrationEnvironment: RegistrationEnvironment = RegistrationEnvironment(),
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
        val sessionTags = inventorySession.snapshot()
        if (sessionTags.isEmpty()) {
            handleEmptySessionRegistration()
            return
        }

        _state.update { current -> current.copy(registrationState = RegistrationState.Registering) }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.ResultRegistration,
            message = "Result registration started with ${sessionTags.size} tags.",
        )

        val bundle = prepareRegistrationBundle(sessionTags).getOrElse { throwable ->
            handleRegistrationPreparationFailure(throwable)
            return
        }

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
            val result = register(pendingWrite.bundle)
            when (result) {
                is ReadResultRegistrationResult.Success -> {
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
        runCatching { inventorySession.record(read) }
            .onSuccess { tags ->
                _state.update { current -> current.copy(tags = tags) }
                val recordedTag = tags.firstOrNull { tag ->
                    tag.epc == read.epc.trim().uppercase(Locale.US)
                }
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

    private suspend fun prepareRegistrationBundle(
        sessionTags: List<InventoryTag>,
    ): Result<ReadResultRegistrationBundle> = runCatching {
        val workContext = workContextRepository.resolveCurrentWorkContext()
        val ruleBundle = ruleRepository.fetchRuleBundle(workContext)
        val equipmentSnapshot = equipmentMasterRepository.fetchEquipmentSnapshot(workContext)
        val judgementResult = readJudgementService.judge(
            workContext = workContext,
            ruleBundle = ruleBundle,
            equipmentSnapshot = equipmentSnapshot,
            sessionTags = sessionTags,
        )

        readResultBundleFactory.create(
            sessionId = sessionIdFactory(),
            registeredAtEpochMillis = clock(),
            deviceId = registrationEnvironment.deviceId,
            readerType = registrationEnvironment.readerType,
            workContext = workContext,
            ruleBundle = ruleBundle,
            equipmentSnapshot = equipmentSnapshot,
            judgementResult = judgementResult,
            sessionTags = sessionTags,
        )
    }

    private suspend fun handleEmptySessionRegistration() {
        val message = "No tags to register."
        _state.update { current -> current.copy(registrationState = RegistrationState.Failed(message)) }
        appendLog(
            level = AppLogLevel.Warning,
            category = AppLogCategory.ResultRegistration,
            message = message,
        )
    }

    private suspend fun handleRegistrationPreparationFailure(throwable: Throwable) {
        val message = throwable.message ?: "Unknown result registration preparation error."
        _state.update { current -> current.copy(registrationState = RegistrationState.Failed(message)) }
        appendLog(
            level = AppLogLevel.Error,
            category = AppLogCategory.ResultRegistration,
            message = "Result registration preparation failed: $message",
        )
    }

    private suspend fun handleRegistrationResult(
        bundle: ReadResultRegistrationBundle,
        result: ReadResultRegistrationResult,
        queueOnFailure: Boolean,
    ) {
        when (result) {
            is ReadResultRegistrationResult.Success -> {
                pendingWriteQueue.remove(bundle.idempotencyKey)
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
                val shouldQueue = queueOnFailure && result.kind == RegistrationFailureKind.Retryable
                if (shouldQueue) {
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
                    message = if (shouldQueue) {
                        "Result registration failed and queued: ${result.message}"
                    } else {
                        "Result registration failed without queue: ${result.message}"
                    },
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
