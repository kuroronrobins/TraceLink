package jp.co.terumo.tracelink.rp902app.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.co.terumo.tracelink.rp902app.data.reader.bluetooth.AndroidReaderPermissionMapper
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothState
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothAddress
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionPreflight
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderPreflightFailure
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Settings 画面用の ViewModel。
 *
 * reader mode、Bluetooth MAC、Android 権限、Bluetooth adapter 状態をまとめて
 * `SettingsUiState` に投影する。real RP902 の接続可否は domain の
 * `ReaderConnectionPreflight` で判定し、画面はその結果を表示するだけにしている。
 */
class SettingsViewModel(
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val readerRuntimeStateRepository: ReaderRuntimeStateRepository,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
) : ViewModel() {
    private val readerAddressInput = MutableStateFlow(
        readerSettingsRepository.settings.value.readerBluetoothAddress?.value.orEmpty(),
    )
    private val readerAddressError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        readerSettingsRepository.settings,
        readerRuntimeStateRepository.runtimeState,
        readerAddressInput,
        readerAddressError,
    ) { settings, runtimeState, addressInput, addressError ->
        val preflight = ReaderConnectionPreflight.evaluate(
            settings = settings,
            sdkInt = sdkIntProvider(),
            grantedPermissions = runtimeState.grantedPermissions,
            bluetoothState = runtimeState.bluetoothState,
        )
        val requiredManifestPermissions = preflight.requiredPermissions.map(
            AndroidReaderPermissionMapper::toManifestPermission,
        )
        val missingManifestPermissions = preflight.missingPermissions.map(
            AndroidReaderPermissionMapper::toManifestPermission,
        )
        SettingsUiState(
            gatewayMode = settings.gatewayMode,
            readerAddressInput = addressInput,
            readerAddressError = addressError,
            bluetoothState = preflight.bluetoothState.displayText(),
            canAttemptConnection = preflight.canAttemptConnection,
            requiredPermissions = preflight.requiredPermissions.map(
                ReaderRuntimePermission::name,
            ),
            missingPermissions = preflight.missingPermissions.map(
                ReaderRuntimePermission::name,
            ),
            requiredManifestPermissions = requiredManifestPermissions,
            missingManifestPermissions = missingManifestPermissions,
            preflightFailures = preflight.failureReasons.map(
                ReaderPreflightFailure::displayText,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(),
    )

    fun updateGatewayMode(mode: ReaderGatewayMode) {
        readerSettingsRepository.updateGatewayMode(mode)
    }

    fun updateReaderAddressInput(value: String) {
        readerAddressInput.value = value
        if (value.isBlank()) {
            // 空欄は「real reader の接続先未設定」として扱う。
            // この状態は preflight で接続不可理由として表示される。
            readerAddressError.value = null
            readerSettingsRepository.updateReaderBluetoothAddress(null)
            return
        }

        // MAC address は domain の値オブジェクトで正規化する。
        // UI 側で文字列のまま持ち続けると、gateway が不正な値で接続を試してしまう。
        val parsedAddress = ReaderBluetoothAddress.parse(value)
        if (parsedAddress == null) {
            readerAddressError.value = "Use 00:11:22:33:44:55 or 001122334455 format."
            return
        }

        readerAddressError.value = null
        readerSettingsRepository.updateReaderBluetoothAddress(parsedAddress)
    }

    fun updatePermissionSnapshot(grantResults: Map<String, Boolean>) {
        // Android の manifest permission 文字列を domain の enum に戻す。
        // domain 層に Android 依存の文字列を持ち込まないための変換点。
        val currentPermissions = readerRuntimeStateRepository.runtimeState.value.grantedPermissions
        val changedPermissions = grantResults.keys
            .mapNotNull(AndroidReaderPermissionMapper::fromManifestPermission)
            .toSet()
        val nextPermissions = currentPermissions
            .filterNot(changedPermissions::contains)
            .toMutableSet()

        grantResults.forEach { (manifestPermission, isGranted) ->
            val permission = AndroidReaderPermissionMapper.fromManifestPermission(manifestPermission)
            if (permission != null && isGranted) {
                nextPermissions += permission
            }
        }

        readerRuntimeStateRepository.updateGrantedPermissions(nextPermissions)
    }

    fun updateBluetoothState(state: ReaderBluetoothState) {
        readerRuntimeStateRepository.updateBluetoothState(state)
    }
}

class SettingsViewModelFactory(
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val readerRuntimeStateRepository: ReaderRuntimeStateRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            readerSettingsRepository = readerSettingsRepository,
            readerRuntimeStateRepository = readerRuntimeStateRepository,
        ) as T
    }
}

private fun ReaderBluetoothState.displayText(): String = when (this) {
    ReaderBluetoothState.Unknown -> "Unknown"
    ReaderBluetoothState.Enabled -> "Enabled"
    ReaderBluetoothState.Disabled -> "Disabled"
    ReaderBluetoothState.Unavailable -> "Unavailable"
    ReaderBluetoothState.PermissionMissing -> "Permission missing"
}

private fun ReaderPreflightFailure.displayText(): String = when (this) {
    ReaderPreflightFailure.BluetoothAddressMissing -> "RP902 Bluetooth MAC address is not set."
    ReaderPreflightFailure.RuntimePermissionsMissing -> "Required Android runtime permission is missing."
    ReaderPreflightFailure.BluetoothStatusUnknown -> "Bluetooth status has not been refreshed."
    ReaderPreflightFailure.BluetoothDisabled -> "Bluetooth is disabled."
    ReaderPreflightFailure.BluetoothUnavailable -> "Bluetooth adapter is unavailable."
    ReaderPreflightFailure.BluetoothStatusPermissionMissing ->
        "Bluetooth status cannot be checked until permission is granted."
}
