package jp.co.terumo.tracelink.rp902app.data.reader.real

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRp902GatewayTest {
    @Test
    fun connect_reportsMissingAddressBeforeUsingVendorSdk() = runBlocking {
        val gateway = RealRp902Gateway()

        gateway.connect()

        val state = gateway.connectionState.value
        assertTrue(state is ReaderConnectionState.Error)
        assertTrue((state as ReaderConnectionState.Error).message.contains("Bluetooth address"))
    }

    @Test
    fun disconnect_resetsStateToDisconnected() = runBlocking {
        val gateway = RealRp902Gateway()

        gateway.connect()
        gateway.disconnect()

        assertEquals(ReaderConnectionState.Disconnected, gateway.connectionState.value)
    }

    @Test
    fun startInventory_reportsAdapterBoundaryUntilRealSdkIsWired() = runBlocking {
        val gateway = RealRp902Gateway()

        gateway.startInventory()

        val state = gateway.connectionState.value
        assertTrue(state is ReaderConnectionState.Error)
        assertTrue((state as ReaderConnectionState.Error).message.contains("Connect RP902"))
    }
}
