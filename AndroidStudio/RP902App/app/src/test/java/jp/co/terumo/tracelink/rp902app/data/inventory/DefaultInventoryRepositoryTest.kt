package jp.co.terumo.tracelink.rp902app.data.inventory

import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.upload.InMemoryUploadRetryQueue
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEvent
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRepository
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadResult
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultInventoryRepositoryTest {
    @Test
    fun connectStartStop_updatesRepositoryState() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val repository = repository(readerGateway = readerGateway)

        try {
            repository.connect()
            settle()
            assertEquals(ReaderConnectionState.Connected, repository.state.value.connectionState)

            repository.startInventory()
            settle()
            assertTrue(repository.state.value.isInventoryRunning)

            repository.stopInventory()
            settle()
            assertFalse(repository.state.value.isInventoryRunning)
        } finally {
            repository.close()
        }
    }

    @Test
    fun tagReads_updateSessionWithDuplicateFilteringAndStructuredLog() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val repository = repository(readerGateway = readerGateway)

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            readerGateway.emitTag(" e2806894000040035a1f90a1 ", 2000L)
            settle()

            val state = repository.state.value
            val tag = state.tags.single()

            assertEquals("E2806894000040035A1F90A1", tag.epc)
            assertEquals(2, tag.readCount)
            assertTrue(
                state.logs.any { log ->
                    log.category == AppLogCategory.Inventory &&
                        log.message.contains("Read EPC")
                },
            )
        } finally {
            repository.close()
        }
    }

    @Test
    fun uploadFailure_setsFailedStateAndQueuesPayloadForRetry() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val uploadRepository = RecordingUploadRepository(
            results = mutableListOf(UploadResult.Failure("network down")),
        )
        val repository = repository(
            readerGateway = readerGateway,
            uploadRepository = uploadRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()

            repository.uploadSession()
            settle()

            val state = repository.state.value
            val uploadState = state.uploadState

            assertTrue(uploadState is UploadState.Failed)
            assertEquals("network down", (uploadState as UploadState.Failed).message)
            assertEquals(1, state.pendingUploads.size)
            assertEquals("session-1", state.pendingUploads.single().payload.sessionId)
            assertEquals(1, state.pendingUploads.single().attemptCount)
            assertEquals(1, uploadRepository.payloads.size)
        } finally {
            repository.close()
        }
    }

    @Test
    fun readerGatewayEvents_areStoredAsStructuredReaderLogs() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val repository = repository(readerGateway = readerGateway)

        try {
            readerGateway.emitEvent(
                ReaderGatewayEvent(
                    level = ReaderGatewayEventLevel.Warning,
                    message = "Preflight result: blocked",
                ),
            )
            settle()

            assertTrue(
                repository.state.value.logs.any { log ->
                    log.category == AppLogCategory.Reader &&
                        log.level == AppLogLevel.Warning &&
                        log.message == "Preflight result: blocked"
                },
            )
        } finally {
            repository.close()
        }
    }

    @Test
    fun retryPendingUploads_sendsQueuedPayloadAndClearsQueueOnSuccess() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val uploadRepository = RecordingUploadRepository(
            results = mutableListOf(
                UploadResult.Failure("network down"),
                UploadResult.Success,
            ),
        )
        val repository = repository(
            readerGateway = readerGateway,
            uploadRepository = uploadRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()
            repository.uploadSession()
            settle()

            repository.retryPendingUploads()
            settle()

            val state = repository.state.value

            assertEquals(2, uploadRepository.payloads.size)
            assertEquals("session-1", uploadRepository.payloads[1].sessionId)
            assertEquals(emptyList<Any>(), state.pendingUploads)
            assertEquals(UploadState.Completed("session-1"), state.uploadState)
        } finally {
            repository.close()
        }
    }

    private fun repository(
        readerGateway: ReaderGateway,
        uploadRepository: UploadRepository = RecordingUploadRepository(),
    ): DefaultInventoryRepository = DefaultInventoryRepository(
        readerGateway = readerGateway,
        uploadRepository = uploadRepository,
        uploadRetryQueue = InMemoryUploadRetryQueue(),
        eventLogStore = InMemoryEventLogStore(),
        clock = IncrementingClock(startAtEpochMillis = 10_000L)::now,
        sessionIdFactory = { "session-1" },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private suspend fun settle() {
        delay(20L)
    }

    private class IncrementingClock(startAtEpochMillis: Long) {
        private var current = startAtEpochMillis

        fun now(): Long {
            val value = current
            current += 100L
            return value
        }
    }

    private class ManualReaderGateway : ReaderGateway {
        private val _connectionState = MutableStateFlow<ReaderConnectionState>(
            ReaderConnectionState.Disconnected,
        )
        override val connectionState: StateFlow<ReaderConnectionState> =
            _connectionState.asStateFlow()

        private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 16)
        override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

        private val _events = MutableSharedFlow<ReaderGatewayEvent>(extraBufferCapacity = 16)
        override val events: Flow<ReaderGatewayEvent> = _events.asSharedFlow()

        override suspend fun connect() {
            _connectionState.value = ReaderConnectionState.Connected
        }

        override suspend fun disconnect() {
            _connectionState.value = ReaderConnectionState.Disconnected
        }

        override suspend fun startInventory() = Unit

        override suspend fun stopInventory() = Unit

        suspend fun emitTag(epc: String, seenAtEpochMillis: Long) {
            _tagReads.emit(
                ReaderTagRead(
                    epc = epc,
                    seenAtEpochMillis = seenAtEpochMillis,
                ),
            )
        }

        suspend fun emitEvent(event: ReaderGatewayEvent) {
            _events.emit(event)
        }
    }

    private class RecordingUploadRepository(
        private val results: MutableList<UploadResult> = mutableListOf(UploadResult.Success),
    ) : UploadRepository {
        val payloads = mutableListOf<InventoryUploadPayload>()

        override suspend fun upload(payload: InventoryUploadPayload): UploadResult {
            payloads += payload
            return if (results.isEmpty()) {
                UploadResult.Success
            } else {
                results.removeAt(0)
            }
        }
    }
}
