package jp.co.terumo.tracelink.rp902app.data.inventory

import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.readresult.InMemoryPendingWriteQueue
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementStatus
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEvent
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayEventLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationFailureKind
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationState
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContextRepository
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
    fun resultRegistrationFailure_setsFailedStateAndQueuesPendingWrite() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val readResultRepository = RecordingReadResultRepository(
            results = mutableListOf(ReadResultRegistrationResult.Failure("network down")),
        )
        val repository = repository(
            readerGateway = readerGateway,
            readResultRepository = readResultRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()

            repository.registerCurrentSessionResults()
            settle()

            val state = repository.state.value
            val registrationState = state.registrationState

            assertTrue(registrationState is RegistrationState.Failed)
            assertEquals("network down", (registrationState as RegistrationState.Failed).message)
            assertEquals(1, state.pendingWrites.size)
            assertEquals("session-1", state.pendingWrites.single().bundle.sessionId)
            assertEquals(1, state.pendingWrites.single().attemptCount)
            assertEquals(1, readResultRepository.bundles.size)
            assertEquals("fake-work-001", readResultRepository.bundles.single().workId)
            assertEquals(
                ReadJudgementStatus.Accepted,
                readResultRepository.bundles.single().tags.single().judgementStatus,
            )
        } finally {
            repository.close()
        }
    }

    @Test
    fun registrationPreparationFailure_setsFailedStateWithoutPendingWrite() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val readResultRepository = RecordingReadResultRepository()
        val repository = repository(
            readerGateway = readerGateway,
            workContextRepository = FailingWorkContextRepository(),
            readResultRepository = readResultRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()

            repository.registerCurrentSessionResults()
            settle()

            val state = repository.state.value
            val registrationState = state.registrationState

            assertTrue(registrationState is RegistrationState.Failed)
            assertEquals(
                "work context unavailable",
                (registrationState as RegistrationState.Failed).message,
            )
            assertEquals(emptyList<Any>(), state.pendingWrites)
            assertEquals(emptyList<Any>(), readResultRepository.bundles)
        } finally {
            repository.close()
        }
    }

    @Test
    fun nonRetryableRegistrationFailure_setsFailedStateWithoutPendingWrite() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val readResultRepository = RecordingReadResultRepository(
            results = mutableListOf(
                ReadResultRegistrationResult.Failure(
                    message = "schema mismatch",
                    kind = RegistrationFailureKind.Contract,
                ),
            ),
        )
        val repository = repository(
            readerGateway = readerGateway,
            readResultRepository = readResultRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()

            repository.registerCurrentSessionResults()
            settle()

            val state = repository.state.value
            val registrationState = state.registrationState

            assertTrue(registrationState is RegistrationState.Failed)
            assertEquals("schema mismatch", (registrationState as RegistrationState.Failed).message)
            assertEquals(emptyList<Any>(), state.pendingWrites)
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
    fun retryPendingWrites_registersQueuedBundleAndClearsQueueOnSuccess() = runBlocking {
        val readerGateway = ManualReaderGateway()
        val readResultRepository = RecordingReadResultRepository(
            results = mutableListOf(
                ReadResultRegistrationResult.Failure("network down"),
                ReadResultRegistrationResult.Success(),
            ),
        )
        val repository = repository(
            readerGateway = readerGateway,
            readResultRepository = readResultRepository,
        )

        try {
            readerGateway.emitTag("E2806894000040035A1F90A1", 1000L)
            settle()
            repository.registerCurrentSessionResults()
            settle()

            repository.retryPendingWrites()
            settle()

            val state = repository.state.value

            assertEquals(2, readResultRepository.bundles.size)
            assertEquals("session-1", readResultRepository.bundles[1].sessionId)
            assertEquals(emptyList<Any>(), state.pendingWrites)
            assertEquals(RegistrationState.Completed("session-1"), state.registrationState)
        } finally {
            repository.close()
        }
    }

    private fun repository(
        readerGateway: ReaderGateway,
        workContextRepository: WorkContextRepository = StaticWorkContextRepository(),
        readResultRepository: ReadResultRepository = RecordingReadResultRepository(),
    ): DefaultInventoryRepository = DefaultInventoryRepository(
        readerGateway = readerGateway,
        workContextRepository = workContextRepository,
        readResultRepository = readResultRepository,
        pendingWriteQueue = InMemoryPendingWriteQueue(),
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

    private class StaticWorkContextRepository : WorkContextRepository {
        override suspend fun resolveCurrentWorkContext(): WorkContext = WorkContext(
            workId = "fake-work-001",
            reportId = "fake-report-001",
            operatorId = "fake-operator",
            startedAtEpochMillis = 0L,
        )
    }

    private class FailingWorkContextRepository : WorkContextRepository {
        override suspend fun resolveCurrentWorkContext(): WorkContext {
            error("work context unavailable")
        }
    }

    private class RecordingReadResultRepository(
        private val results: MutableList<ReadResultRegistrationResult> = mutableListOf(
            ReadResultRegistrationResult.Success(),
        ),
    ) : ReadResultRepository {
        val bundles = mutableListOf<ReadResultRegistrationBundle>()

        override suspend fun register(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult {
            bundles += bundle
            return if (results.isEmpty()) {
                ReadResultRegistrationResult.Success()
            } else {
                results.removeAt(0)
            }
        }
    }
}
