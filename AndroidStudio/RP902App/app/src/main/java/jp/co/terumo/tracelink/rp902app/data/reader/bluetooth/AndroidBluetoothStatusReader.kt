package jp.co.terumo.tracelink.rp902app.data.reader.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothState

/**
 * Android の Bluetooth adapter 状態を app-owned な `ReaderBluetoothState` に変換する helper。
 *
 * Android 12+ では状態確認自体に権限が必要な場合があるため、`SecurityException` は例外として
 * 外へ投げず `PermissionMissing` に変換する。
 */
object AndroidBluetoothStatusReader {
    @SuppressLint("MissingPermission")
    fun read(context: Context): ReaderBluetoothState {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            ?: return ReaderBluetoothState.Unavailable
        val adapter = bluetoothManager.adapter
            ?: return ReaderBluetoothState.Unavailable

        return runCatching {
            if (adapter.isEnabled) {
                ReaderBluetoothState.Enabled
            } else {
                ReaderBluetoothState.Disabled
            }
        }.getOrElse { throwable ->
            if (throwable is SecurityException) {
                ReaderBluetoothState.PermissionMissing
            } else {
                ReaderBluetoothState.Unknown
            }
        }
    }
}
