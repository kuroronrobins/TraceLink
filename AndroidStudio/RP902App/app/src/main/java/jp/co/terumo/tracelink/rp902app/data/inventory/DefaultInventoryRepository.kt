package jp.co.terumo.tracelink.rp902app.data.inventory

import java.util.UUID
import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.reader.FakeReaderGateway
import jp.co.terumo.tracelink.rp902app.data.upload.FakeUploadRepository
import jp.co.terumo.tracelink.rp902app.data.upload.InMemoryUploadRetryQueue
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
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRepository
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadResult
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRetryQueue
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState
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
 * reader からの tag read、session 内重複除去、upload、retry queue、structured log を
 * ここで組み合わせる。vendor SDK の詳細は `ReaderGateway` の内側に、HTTP の詳細は
 * `UploadRepository` の内側に閉じ込めることで、UI はこの repository state だけを見ればよい。
 */
class DefaultInventoryRepository(
    private val readerGateway: ReaderGateway = FakeReaderGateway(),
    private val uploadRepository: UploadRepository = FakeUploadRepository(),
    private val uploadRetryQueue: UploadRetryQueue = InMemoryUploadRetryQueue(),
    private val eventLogStore: EventLogStore = InMemoryEventLogStore(),
    private val inventorySession: InventorySession = InventorySession(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InventoryRepository {
    private val _state = MutableStateFlow(
        InventoryRepositoryState(
            logs = eventLogStore.logs.value,
            pendingUploads = uploadRetryQueue.pendingUploads.value,
        ),
    )
    override val state: StateFlow<InventoryRepositoryState> = _state.asStateFlow()

    init {
        // LogStore / RetryQueue / ReaderGateway の Flow を repository state に集約する。
        // ここが Inventory 画面と Logs 画面の source of truth になる。
        eventLogStore.logs
            .onEach { logs ->
                _state.update { current -> current.copy(logs = logs) }
            }
            .launchIn(scope)

        uploadRetryQueue.pendingUploads
            .onEach { pendingUploads ->
                _state.update { current -> current.copy(pendingUploads = pendingUploads) }
            }
            .launchIn(scope)

        readerGateway.connectionState
            .onEach { connectionState ->
                _state.update { current ->
                    current.copy(
                        connectionState = connectionState,
                        // 接続が切れた場合、画面上の inventory running も止める。
                        // 実機側の stop 完了は gateway log で別途確認する。
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
            // tag read は gateway から非同期に流れてくる。
            // 重複除去と state 更新は recordRead に閉じ込め、UI からは直接触らない。
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
                uploadState = UploadState.Idle,
            )
        }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.Inventory,
            message = "Session cleared.",
        )
    }

    override suspend fun uploadSession() {
        // upload は現在の session snapshot を payload 化してから実行する。
        // 空 session は backend へ送らず、画面に失敗状態として返す。
        val payload = inventorySession.toUploadPayload(
            sessionId = sessionIdFactory(),
            sentAtEpochMillis = clock(),
            deviceId = "android-local-device",
            readerType = "RP902",
        )

        if (payload.tags.isEmpty()) {
            val message = "No tags to upload."
            _state.update { current -> current.copy(uploadState = UploadState.Failed(message)) }
            appendLog(
                level = AppLogLevel.Warning,
                category = AppLogCategory.Upload,
                message = message,
            )
            return
        }

        _state.update { current -> current.copy(uploadState = UploadState.Uploading) }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.Upload,
            message = "Upload started with ${payload.tags.size} tags.",
        )

        handleUploadResult(
            payload = payload,
            result = upload(payload),
            queueOnFailure = true,
        )
    }

    override suspend fun retryPendingUploads() {
        val pendingUploads = uploadRetryQueue.pendingUploads.value
        if (pendingUploads.isEmpty()) {
            appendLog(
                level = AppLogLevel.Info,
                category = AppLogCategory.Upload,
                message = "No queued uploads to retry.",
            )
            return
        }

        _state.update { current -> current.copy(uploadState = UploadState.Uploading) }
        appendLog(
            level = AppLogLevel.Info,
            category = AppLogCategory.Upload,
            message = "Retry started for ${pendingUploads.size} queued uploads.",
        )

        var lastCompletedSessionId: String? = null
        var lastFailureMessage: String? = null

        pendingUploads.forEach { pendingUpload ->
            // 失敗が残っても他の pending upload は続けて試す。
            // 長期運用では network/backoff 方針をここから分離する可能性がある。
            val result = upload(pendingUpload.payload)
            when (result) {
                UploadResult.Success -> {
                    uploadRetryQueue.remove(pendingUpload.id)
                    lastCompletedSessionId = pendingUpload.payload.sessionId
                    appendLog(
                        level = AppLogLevel.Info,
                        category = AppLogCategory.Upload,
                        message = "Queued upload completed: ${pendingUpload.payload.sessionId}",
                    )
                }

                is UploadResult.Failure -> {
                    lastFailureMessage = result.message
                    uploadRetryQueue.markAttemptFailed(
                        pendingUploadId = pendingUpload.id,
                        failedAtEpochMillis = clock(),
                        message = result.message,
                    )
                    appendLog(
                        level = AppLogLevel.Warning,
                        category = AppLogCategory.Upload,
                        message = "Queued upload failed: ${result.message}",
                    )
                }
            }
        }

        _state.update { current ->
            current.copy(
                uploadState = lastFailureMessage?.let(UploadState::Failed)
                    ?: UploadState.Completed(lastCompletedSessionId.orEmpty()),
            )
        }
    }

    override fun close() {
        readerGateway.close()
        scope.cancel()
    }

    private suspend fun recordRead(read: ReaderTagRead) {
        // InventorySession は blank EPC などを拒否することがある。
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

    private suspend fun handleUploadResult(
        payload: InventoryUploadPayload,
        result: UploadResult,
        queueOnFailure: Boolean,
    ) {
        // upload transport と retry queue を分けておくことで、
        // 本物 backend と永続 retry storage を別々に差し替えられる。
        when (result) {
            UploadResult.Success -> {
                uploadRetryQueue.remove(payload.sessionId)
                _state.update { current ->
                    current.copy(uploadState = UploadState.Completed(payload.sessionId))
                }
                appendLog(
                    level = AppLogLevel.Info,
                    category = AppLogCategory.Upload,
                    message = "Upload completed.",
                )
            }

            is UploadResult.Failure -> {
                if (queueOnFailure) {
                    uploadRetryQueue.enqueueFailure(
                        payload = payload,
                        failedAtEpochMillis = clock(),
                        message = result.message,
                    )
                }
                _state.update { current ->
                    current.copy(uploadState = UploadState.Failed(result.message))
                }
                appendLog(
                    level = AppLogLevel.Warning,
                    category = AppLogCategory.Upload,
                    message = "Upload failed and queued: ${result.message}",
                )
            }
        }
    }

    private suspend fun upload(payload: InventoryUploadPayload): UploadResult =
        runCatching { uploadRepository.upload(payload) }
            .getOrElse { throwable ->
                UploadResult.Failure(throwable.message ?: "Unknown upload error.")
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
