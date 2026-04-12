package jp.co.terumo.tracelink.rp902app.data.reader.bluetooth

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Android runtime permission と Bluetooth 状態の最新 snapshot を保持する in-memory store。
 *
 * ここに保存するのは app-owned な状態だけで、Android の permission 文字列や UI 表示文言は
 * 別の層で変換する。real gateway の preflight もこの snapshot を参照する。
 */
class InMemoryReaderRuntimeStateRepository(
    initialState: ReaderRuntimeState = ReaderRuntimeState(),
) : ReaderRuntimeStateRepository {
    private val _runtimeState = MutableStateFlow(initialState)
    override val runtimeState: StateFlow<ReaderRuntimeState> = _runtimeState.asStateFlow()

    override fun updateGrantedPermissions(permissions: Set<ReaderRuntimePermission>) {
        _runtimeState.update { current ->
            current.copy(grantedPermissions = permissions)
        }
    }

    override fun updateBluetoothState(state: ReaderBluetoothState) {
        _runtimeState.update { current ->
            current.copy(bluetoothState = state)
        }
    }
}
