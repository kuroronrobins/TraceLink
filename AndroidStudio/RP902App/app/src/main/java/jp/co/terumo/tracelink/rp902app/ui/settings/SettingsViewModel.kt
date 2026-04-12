package jp.co.terumo.tracelink.rp902app.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderBluetoothAddress
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionPreflight
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimePermission
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
) : ViewModel() {
    private val readerAddressInput = MutableStateFlow(
        readerSettingsRepository.settings.value.readerBluetoothAddress?.value.orEmpty(),
    )
    private val readerAddressError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        readerSettingsRepository.settings,
        readerAddressInput,
        readerAddressError,
    ) { settings, addressInput, addressError ->
        val preflight = ReaderConnectionPreflight.evaluate(
            settings = settings,
            sdkInt = sdkIntProvider(),
            grantedPermissions = emptySet(),
            isBluetoothEnabled = false,
        )
        SettingsUiState(
            gatewayMode = settings.gatewayMode,
            readerAddressInput = addressInput,
            readerAddressError = addressError,
            requiredPermissions = preflight.requiredPermissions.map(
                ReaderRuntimePermission::name,
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
            readerAddressError.value = null
            readerSettingsRepository.updateReaderBluetoothAddress(null)
            return
        }

        val parsedAddress = ReaderBluetoothAddress.parse(value)
        if (parsedAddress == null) {
            readerAddressError.value = "Use 00:11:22:33:44:55 or 001122334455 format."
            return
        }

        readerAddressError.value = null
        readerSettingsRepository.updateReaderBluetoothAddress(parsedAddress)
    }
}

class SettingsViewModelFactory(
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(readerSettingsRepository) as T
    }
}
