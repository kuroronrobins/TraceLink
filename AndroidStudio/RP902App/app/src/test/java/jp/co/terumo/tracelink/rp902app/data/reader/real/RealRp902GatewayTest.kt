package jp.co.terumo.tracelink.rp902app.data.reader.real

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRp902GatewayTest {
    @Test
    fun connect_reportsNotEnabledWithoutUsingVendorSdk() = runBlocking {
        val gateway = RealRp902Gateway()

        gateway.connect()

        val state = gateway.connectionState.value
        assertTrue(state is ReaderConnectionState.Error)
        assertTrue((state as ReaderConnectionState.Error).message.contains("not enabled"))
        assertTrue(state.message.contains("Bluetooth address is not configured"))
    }

    @Test
    fun disconnect_resetsStateToDisconnected() = runBlocking {
        val gateway = RealRp902Gateway(
            RealRp902GatewayConfiguration(bluetoothAddress = "00:11:22:33:44:55"),
        )

        gateway.connect()
        gateway.disconnect()

        assertEquals(ReaderConnectionState.Disconnected, gateway.connectionState.value)
    }

    @Test
    fun startInventory_reportsAdapterBoundaryUntilRealSdkIsWired() = runBlocking {
        val gateway = RealRp902Gateway(
            RealRp902GatewayConfiguration(bluetoothAddress = "00:11:22:33:44:55"),
        )

        gateway.startInventory()

        val state = gateway.connectionState.value
        assertTrue(state is ReaderConnectionState.Error)
        assertTrue((state as ReaderConnectionState.Error).message.contains("start inventory"))
    }
}
