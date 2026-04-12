package jp.co.terumo.tracelink.rp902app.data.reader

import jp.co.terumo.tracelink.rp902app.data.settings.InMemoryReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothAddress
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
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
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableReaderGatewayTest {
    @Test
    fun defaultSettingsUseFakeGateway() = runBlocking {
        val settingsRepository = InMemoryReaderSettingsRepository()
        val fakeGateway = ManualGateway()
        val realGateway = ManualGateway()
        val gateway = configurableGateway(
            settingsRepository = settingsRepository,
            fakeGateway = fakeGateway,
            realGateway = realGateway,
        )

        try {
            gateway.connect()
            settle()

            assertTrue(fakeGateway.connectCalled)
            assertEquals(ReaderConnectionState.Connected, gateway.connectionState.value)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun modeChangeSwitchesActiveGateway() = runBlocking {
        val settingsRepository = InMemoryReaderSettingsRepository()
        val fakeGateway = ManualGateway()
        val realGateway = ManualGateway()
        val gateway = configurableGateway(
            settingsRepository = settingsRepository,
            fakeGateway = fakeGateway,
            realGateway = realGateway,
        )

        try {
            settingsRepository.updateGatewayMode(ReaderGatewayMode.RealRp902)
            settle()
            gateway.connect()
            settle()

            assertTrue(fakeGateway.disconnectCalled)
            assertTrue(realGateway.connectCalled)
            assertEquals(ReaderConnectionState.Connected, gateway.connectionState.value)
        } finally {
            gateway.close()
        }
    }

    @Test
    fun addressChangeWhileFakeModeDoesNotRecreateGateway() = runBlocking {
        val settingsRepository = InMemoryReaderSettingsRepository()
        val fakeGateway = ManualGateway()
        val realGateway = ManualGateway()
        val gateway = configurableGateway(
            settingsRepository = settingsRepository,
            fakeGateway = fakeGateway,
            realGateway = realGateway,
        )

        try {
            settingsRepository.updateReaderBluetoothAddress(
                ReaderBluetoothAddress.parse("00:11:22:33:44:55"),
            )
            settle()

            assertEquals(0, fakeGateway.disconnectCount)
            assertEquals(ReaderConnectionState.Disconnected, gateway.connectionState.value)
        } finally {
            gateway.close()
        }
    }

    private fun configurableGateway(
        settingsRepository: InMemoryReaderSettingsRepository,
        fakeGateway: ReaderGateway,
        realGateway: ReaderGateway,
    ): ConfigurableReaderGateway = ConfigurableReaderGateway(
        settingsRepository = settingsRepository,
        fakeGatewayFactory = { fakeGateway },
        realGatewayFactory = { _: ReaderSettings -> realGateway },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private suspend fun settle() {
        delay(20L)
    }

    private class ManualGateway : ReaderGateway {
        private val _connectionState = MutableStateFlow<ReaderConnectionState>(
            ReaderConnectionState.Disconnected,
        )
        override val connectionState: StateFlow<ReaderConnectionState> =
            _connectionState.asStateFlow()

        private val _tagReads = MutableSharedFlow<ReaderTagRead>(extraBufferCapacity = 16)
        override val tagReads: Flow<ReaderTagRead> = _tagReads.asSharedFlow()

        var connectCalled = false
            private set
        var disconnectCalled = false
            private set
        var disconnectCount = 0
            private set

        override suspend fun connect() {
            connectCalled = true
            _connectionState.value = ReaderConnectionState.Connected
        }

        override suspend fun disconnect() {
            disconnectCalled = true
            disconnectCount++
            _connectionState.value = ReaderConnectionState.Disconnected
        }

        override suspend fun startInventory() = Unit

        override suspend fun stopInventory() = Unit
    }
}
