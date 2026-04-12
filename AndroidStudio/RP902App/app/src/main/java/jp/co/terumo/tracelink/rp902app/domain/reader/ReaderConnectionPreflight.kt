package jp.co.terumo.tracelink.rp902app.domain.reader

enum class ReaderRuntimePermission {
    Bluetooth,
    BluetoothAdmin,
    BluetoothConnect,
    BluetoothScan,
    AccessFineLocation,
    AccessCoarseLocation,
}

data class ReaderConnectionPreflightState(
    val requiredPermissions: List<ReaderRuntimePermission>,
    val missingPermissions: List<ReaderRuntimePermission>,
    val requiresBluetoothAddress: Boolean,
    val hasBluetoothAddress: Boolean,
    val isBluetoothEnabled: Boolean,
    val canAttemptConnection: Boolean,
)

object ReaderConnectionPreflight {
    private const val ANDROID_12_API = 31

    fun requiredPermissionsForSdk(sdkInt: Int): List<ReaderRuntimePermission> =
        if (sdkInt >= ANDROID_12_API) {
            listOf(
                ReaderRuntimePermission.BluetoothConnect,
                ReaderRuntimePermission.BluetoothScan,
            )
        } else {
            listOf(
                ReaderRuntimePermission.Bluetooth,
                ReaderRuntimePermission.BluetoothAdmin,
                ReaderRuntimePermission.AccessFineLocation,
                ReaderRuntimePermission.AccessCoarseLocation,
            )
        }

    fun evaluate(
        settings: ReaderSettings,
        sdkInt: Int,
        grantedPermissions: Set<ReaderRuntimePermission>,
        isBluetoothEnabled: Boolean,
    ): ReaderConnectionPreflightState {
        val requiresRealReader = settings.gatewayMode == ReaderGatewayMode.RealRp902
        val requiredPermissions = if (requiresRealReader) {
            requiredPermissionsForSdk(sdkInt)
        } else {
            emptyList()
        }
        val missingPermissions = requiredPermissions.filterNot(grantedPermissions::contains)
        val requiresBluetoothAddress = requiresRealReader
        val hasBluetoothAddress = settings.readerBluetoothAddress != null

        return ReaderConnectionPreflightState(
            requiredPermissions = requiredPermissions,
            missingPermissions = missingPermissions,
            requiresBluetoothAddress = requiresBluetoothAddress,
            hasBluetoothAddress = hasBluetoothAddress,
            isBluetoothEnabled = !requiresRealReader || isBluetoothEnabled,
            canAttemptConnection = !requiresRealReader ||
                (
                    hasBluetoothAddress &&
                        isBluetoothEnabled &&
                        missingPermissions.isEmpty()
                    ),
        )
    }
}
