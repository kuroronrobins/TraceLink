package jp.co.terumo.tracelink.rp902app.ui.settings

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode

data class SettingsUiState(
    val gatewayMode: ReaderGatewayMode = ReaderGatewayMode.Fake,
    val readerAddressInput: String = "",
    val readerAddressError: String? = null,
    val requiredPermissions: List<String> = emptyList(),
)
