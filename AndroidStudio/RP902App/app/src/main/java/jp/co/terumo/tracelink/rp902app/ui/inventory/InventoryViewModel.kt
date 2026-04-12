package jp.co.terumo.tracelink.rp902app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import jp.co.terumo.tracelink.rp902app.data.reader.FakeReaderGateway
import jp.co.terumo.tracelink.rp902app.data.upload.FakeUploadRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventorySession
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.displayText
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRepository
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val readerGateway: ReaderGateway = FakeReaderGateway(),
    private val uploadRepository: UploadRepository = FakeUploadRepository(),
    private val inventorySession: InventorySession = InventorySession(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        InventoryUiState(logs = listOf(logLine("Session initialized."))),
    )
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        readerGateway.connectionState
            .onEach { connectionState ->
                _uiState.update { state ->
                    state.copy(connectionState = connectionState)
                        .withLog("Reader: ${connectionState.displayText()}")
                }
            }
            .launchIn(viewModelScope)

        readerGateway.tagReads
            .onEach { read ->
                val tags = inventorySession.record(read)
                _uiState.update { state ->
                    state.copy(tags = tags)
                        .withLog("Read EPC ${read.epc}")
                }
            }
            .launchIn(viewModelScope)
    }

    fun connect() {
        runReaderCommand("Connect failed.") {
            readerGateway.connect()
        }
    }

    fun disconnect() {
        runReaderCommand("Disconnect failed.") {
            readerGateway.disconnect()
            _uiState.update { state ->
                state.copy(isInventoryRunning = false)
                    .withLog("Inventory stopped by disconnect.")
            }
        }
    }

    fun startInventory() {
        runReaderCommand("Start inventory failed.") {
            readerGateway.startInventory()
            if (readerGateway.connectionState.value == ReaderConnectionState.Connected) {
                _uiState.update { state ->
                    state.copy(isInventoryRunning = true)
                        .withLog("Inventory started.")
                }
            }
        }
    }

    fun stopInventory() {
        runReaderCommand("Stop inventory failed.") {
            readerGateway.stopInventory()
            _uiState.update { state ->
                state.copy(isInventoryRunning = false)
                    .withLog("Inventory stopped.")
            }
        }
    }

    fun clearSession() {
        inventorySession.clear()
        _uiState.update { state ->
            state.copy(
                tags = emptyList(),
                uploadState = UploadState.Idle,
            ).withLog("Session cleared.")
        }
    }

    fun uploadSession() {
        viewModelScope.launch {
            val payload = inventorySession.toUploadPayload(
                sessionId = UUID.randomUUID().toString(),
                sentAtEpochMillis = clock(),
                deviceId = "android-local-device",
                readerType = "RP902",
            )

            _uiState.update { state ->
                state.copy(uploadState = UploadState.Uploading)
                    .withLog("Upload started with ${payload.tags.size} tags.")
            }

            when (val result = uploadRepository.upload(payload)) {
                UploadResult.Success -> {
                    _uiState.update { state ->
                        state.copy(uploadState = UploadState.Completed(payload.sessionId))
                            .withLog("Upload completed.")
                    }
                }

                is UploadResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(uploadState = UploadState.Failed(result.message))
                            .withLog("Upload failed: ${result.message}")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        readerGateway.close()
        super.onCleared()
    }

    private fun runReaderCommand(errorPrefix: String, command: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { command() }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.withLog("$errorPrefix ${throwable.message.orEmpty()}")
                    }
                }
        }
    }

    private fun InventoryUiState.withLog(message: String): InventoryUiState =
        copy(logs = (listOf(logLine(message)) + logs).take(MaxLogLines))

    private fun logLine(message: String): String = "${clock()}  $message"

    private companion object {
        const val MaxLogLines = 80
    }
}

