package jp.co.terumo.tracelink.rp902app.data.reader.bluetooth

import android.Manifest
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission

/**
 * domain の `ReaderRuntimePermission` と Android manifest permission 文字列の変換点。
 *
 * Android 固有の permission 名を domain 層に入れないため、この mapper を data 層に置いている。
 * OS バージョン別に必要な permission の判断は `ReaderConnectionPreflight` が担当する。
 */
object AndroidReaderPermissionMapper {
    fun toManifestPermission(permission: ReaderRuntimePermission): String = when (permission) {
        ReaderRuntimePermission.Bluetooth -> Manifest.permission.BLUETOOTH
        ReaderRuntimePermission.BluetoothAdmin -> Manifest.permission.BLUETOOTH_ADMIN
        ReaderRuntimePermission.BluetoothConnect -> BLUETOOTH_CONNECT
        ReaderRuntimePermission.BluetoothScan -> BLUETOOTH_SCAN
        ReaderRuntimePermission.AccessFineLocation -> Manifest.permission.ACCESS_FINE_LOCATION
        ReaderRuntimePermission.AccessCoarseLocation -> Manifest.permission.ACCESS_COARSE_LOCATION
    }

    fun fromManifestPermission(permission: String): ReaderRuntimePermission? = when (permission) {
        Manifest.permission.BLUETOOTH -> ReaderRuntimePermission.Bluetooth
        Manifest.permission.BLUETOOTH_ADMIN -> ReaderRuntimePermission.BluetoothAdmin
        BLUETOOTH_CONNECT -> ReaderRuntimePermission.BluetoothConnect
        BLUETOOTH_SCAN -> ReaderRuntimePermission.BluetoothScan
        Manifest.permission.ACCESS_FINE_LOCATION -> ReaderRuntimePermission.AccessFineLocation
        Manifest.permission.ACCESS_COARSE_LOCATION -> ReaderRuntimePermission.AccessCoarseLocation
        else -> null
    }

    private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
}
