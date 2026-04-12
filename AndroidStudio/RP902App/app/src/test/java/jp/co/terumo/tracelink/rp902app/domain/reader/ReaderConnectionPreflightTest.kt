package jp.co.terumo.tracelink.rp902app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderConnectionPreflightTest {
    @Test
    fun requiredPermissionsForSdk_usesLegacyBluetoothAndLocationBeforeAndroid12() {
        assertEquals(
            listOf(
                ReaderRuntimePermission.Bluetooth,
                ReaderRuntimePermission.BluetoothAdmin,
                ReaderRuntimePermission.AccessFineLocation,
                ReaderRuntimePermission.AccessCoarseLocation,
            ),
            ReaderConnectionPreflight.requiredPermissionsForSdk(30),
        )
    }

    @Test
    fun requiredPermissionsForSdk_usesRuntimeBluetoothPermissionsFromAndroid12() {
        assertEquals(
            listOf(
                ReaderRuntimePermission.BluetoothConnect,
                ReaderRuntimePermission.BluetoothScan,
            ),
            ReaderConnectionPreflight.requiredPermissionsForSdk(31),
        )
    }

    @Test
    fun evaluate_fakeModeDoesNotRequireBluetoothReadiness() {
        val state = ReaderConnectionPreflight.evaluate(
            settings = ReaderSettings(),
            sdkInt = 35,
            grantedPermissions = emptySet(),
            isBluetoothEnabled = false,
        )

        assertTrue(state.canAttemptConnection)
        assertEquals(emptyList<ReaderRuntimePermission>(), state.requiredPermissions)
    }

    @Test
    fun evaluate_realModeRequiresAddressBluetoothAndPermissions() {
        val address = ReaderBluetoothAddress.parse("00:11:22:33:44:55")
        val state = ReaderConnectionPreflight.evaluate(
            settings = ReaderSettings(
                gatewayMode = ReaderGatewayMode.RealRp902,
                readerBluetoothAddress = address,
            ),
            sdkInt = 35,
            grantedPermissions = setOf(
                ReaderRuntimePermission.BluetoothConnect,
                ReaderRuntimePermission.BluetoothScan,
            ),
            isBluetoothEnabled = true,
        )

        assertTrue(state.hasBluetoothAddress)
        assertTrue(state.canAttemptConnection)
        assertEquals(emptyList<ReaderRuntimePermission>(), state.missingPermissions)
    }

    @Test
    fun evaluate_realModeBlocksWhenAddressIsMissing() {
        val state = ReaderConnectionPreflight.evaluate(
            settings = ReaderSettings(gatewayMode = ReaderGatewayMode.RealRp902),
            sdkInt = 35,
            grantedPermissions = setOf(
                ReaderRuntimePermission.BluetoothConnect,
                ReaderRuntimePermission.BluetoothScan,
            ),
            isBluetoothEnabled = true,
        )

        assertFalse(state.hasBluetoothAddress)
        assertFalse(state.canAttemptConnection)
    }
}
