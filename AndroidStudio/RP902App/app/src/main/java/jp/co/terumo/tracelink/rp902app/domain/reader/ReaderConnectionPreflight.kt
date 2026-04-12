package jp.co.terumo.tracelink.rp902app.domain.reader

enum class ReaderRuntimePermission {
    Bluetooth,
    BluetoothAdmin,
    BluetoothConnect,
    BluetoothScan,
    AccessFineLocation,
    AccessCoarseLocation,
}

enum class ReaderPreflightFailure {
    BluetoothAddressMissing,
    RuntimePermissionsMissing,
    BluetoothStatusUnknown,
    BluetoothDisabled,
    BluetoothUnavailable,
    BluetoothStatusPermissionMissing,
}

/**
 * real RP902 接続前に必要な条件をまとめた状態。
 *
 * Settings 画面の表示にも、`ConfigurableReaderGateway` が接続を止める判断にも使う。
 */
data class ReaderConnectionPreflightState(
    val requiredPermissions: List<ReaderRuntimePermission>,
    val missingPermissions: List<ReaderRuntimePermission>,
    val requiresBluetoothAddress: Boolean,
    val hasBluetoothAddress: Boolean,
    val bluetoothState: ReaderBluetoothState,
    val isBluetoothEnabled: Boolean,
    val canAttemptConnection: Boolean,
    val failureReasons: List<ReaderPreflightFailure>,
)

/**
 * real RP902 接続前の純粋な判定ロジック。
 *
 * Android runtime permission の要求は OS バージョンで変わるため、ここで app-owned な
 * `ReaderRuntimePermission` として評価する。Android の manifest permission 文字列への変換は
 * data 層の mapper に閉じ込めている。
 */
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
    ): ReaderConnectionPreflightState = evaluate(
        settings = settings,
        sdkInt = sdkInt,
        grantedPermissions = grantedPermissions,
        bluetoothState = if (isBluetoothEnabled) {
            ReaderBluetoothState.Enabled
        } else {
            ReaderBluetoothState.Disabled
        },
    )

    fun evaluate(
        settings: ReaderSettings,
        sdkInt: Int,
        grantedPermissions: Set<ReaderRuntimePermission>,
        bluetoothState: ReaderBluetoothState,
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
        val failures = if (requiresRealReader) {
            buildList {
                if (!hasBluetoothAddress) {
                    add(ReaderPreflightFailure.BluetoothAddressMissing)
                }
                if (missingPermissions.isNotEmpty()) {
                    add(ReaderPreflightFailure.RuntimePermissionsMissing)
                }
                when (bluetoothState) {
                    ReaderBluetoothState.Unknown -> {
                        add(ReaderPreflightFailure.BluetoothStatusUnknown)
                    }

                    ReaderBluetoothState.Enabled -> Unit
                    ReaderBluetoothState.Disabled -> {
                        add(ReaderPreflightFailure.BluetoothDisabled)
                    }

                    ReaderBluetoothState.Unavailable -> {
                        add(ReaderPreflightFailure.BluetoothUnavailable)
                    }

                    ReaderBluetoothState.PermissionMissing -> {
                        add(ReaderPreflightFailure.BluetoothStatusPermissionMissing)
                    }
                }
            }
        } else {
            emptyList()
        }

        return ReaderConnectionPreflightState(
            requiredPermissions = requiredPermissions,
            missingPermissions = missingPermissions,
            requiresBluetoothAddress = requiresBluetoothAddress,
            hasBluetoothAddress = hasBluetoothAddress,
            bluetoothState = bluetoothState,
            isBluetoothEnabled = !requiresRealReader || bluetoothState == ReaderBluetoothState.Enabled,
            canAttemptConnection = failures.isEmpty(),
            failureReasons = failures,
        )
    }
}
