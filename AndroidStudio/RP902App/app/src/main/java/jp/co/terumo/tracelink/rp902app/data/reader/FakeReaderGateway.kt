package jp.co.terumo.tracelink.rp902app.data.reader

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FakeReaderGateway(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val readDelayMillis: Long = 650L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ReaderGateway {
    private val _connectionState = MutableStateFlow<ReaderConnectionState>(
        ReaderConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<ReaderConnectionState> =
        _connectionState.asStateFlow()

    private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 16)
    override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

    private var inventoryJob: Job? = null

    override suspend fun connect() {
        if (_connectionState.value == ReaderConnectionState.Connected) return
        _connectionState.value = ReaderConnectionState.Connecting
        delay(400L)
        _connectionState.value = ReaderConnectionState.Connected
    }

    override suspend fun disconnect() {
        stopInventory()
        _connectionState.value = ReaderConnectionState.Disconnected
    }

    override suspend fun startInventory() {
        if (_connectionState.value != ReaderConnectionState.Connected) {
            _connectionState.value = ReaderConnectionState.Error(
                "Connect the reader before starting inventory.",
            )
            return
        }
        if (inventoryJob?.isActive == true) return

        inventoryJob = scope.launch {
            var index = 0
            while (isActive) {
                _tagReads.emit(
                    ReaderTagRead(
                        epc = fakeEpcs[index % fakeEpcs.size],
                        seenAtEpochMillis = clock(),
                    ),
                )
                index++
                delay(readDelayMillis)
            }
        }
    }

    override suspend fun stopInventory() {
        inventoryJob?.cancel()
        inventoryJob = null
    }

    override fun close() {
        inventoryJob?.cancel()
        scope.cancel()
    }

    companion object {
        private val fakeEpcs = listOf(
            "E2806894000040035A1F90A1",
            "E2806894000050035A1F90A2",
            "E2806894000040035A1F90A1",
            "E2806894000060035A1F90A3",
            "E2806894000050035A1F90A2",
            "E2806894000070035A1F90A4",
        )
    }
}

