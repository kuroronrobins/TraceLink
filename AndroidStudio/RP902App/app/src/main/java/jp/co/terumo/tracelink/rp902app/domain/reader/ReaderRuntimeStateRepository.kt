package jp.co.terumo.tracelink.rp902app.domain.reader

import kotlinx.coroutines.flow.StateFlow

data class ReaderRuntimeState(
    val grantedPermissions: Set<ReaderRuntimePermission> = emptySet(),
    val bluetoothState: ReaderBluetoothState = ReaderBluetoothState.Unknown,
)

enum class ReaderBluetoothState {
    Unknown,
    Enabled,
    Disabled,
    Unavailable,
    PermissionMissing,
}

interface ReaderRuntimeStateRepository {
    val runtimeState: StateFlow<ReaderRuntimeState>

    fun updateGrantedPermissions(permissions: Set<ReaderRuntimePermission>)
    fun updateBluetoothState(state: ReaderBluetoothState)
}
