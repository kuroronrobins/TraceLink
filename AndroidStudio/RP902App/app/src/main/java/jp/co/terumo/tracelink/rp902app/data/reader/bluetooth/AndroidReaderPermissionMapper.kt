package jp.co.terumo.tracelink.rp902app.data.reader.bluetooth

import android.Manifest
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission

object AndroidReaderPermissionMapper {
    fun toManifestPermission(permission: ReaderRuntimePermission): String = when (permission) {
        ReaderRuntimePermission.Bluetooth -> Manifest.permission.BLUETOOTH
        ReaderRuntimePermission.BluetoothAdmin -> Manifest.permission.BLUETOOTH_ADMIN
        ReaderRuntimePermission.BluetoothConnect -> BLUETOOTH_CONNECT
        ReaderRuntimePermission.BluetoothScan -> BLUETOOTH_SCAN
        ReaderRuntimePermission.AccessFineLocation -> Manifest.permission.ACCESS_FINE_LOCATION
        ReaderRuntimePermission.AccessCoarseLocation -> Manifest.permission.ACCESS_COARSE_LOCATION
    }

    private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
}
