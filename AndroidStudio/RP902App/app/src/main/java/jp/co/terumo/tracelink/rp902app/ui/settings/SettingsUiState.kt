package jp.co.terumo.tracelink.rp902app.ui.settings

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode

/**
 * Settings 画面が表示するための状態。
 *
 * preflight の判定結果を文字列リストへ投影しているため、Composable は
 * 権限や Bluetooth 状態の細かい判定ロジックを持たずに表示できる。
 */
data class SettingsUiState(
    val gatewayMode: ReaderGatewayMode = ReaderGatewayMode.Fake,
    val readerAddressInput: String = "",
    val readerAddressError: String? = null,
    val bluetoothState: String = "Not required",
    val canAttemptConnection: Boolean = true,
    val requiredPermissions: List<String> = emptyList(),
    val missingPermissions: List<String> = emptyList(),
    val requiredManifestPermissions: List<String> = emptyList(),
    val missingManifestPermissions: List<String> = emptyList(),
    val preflightFailures: List<String> = emptyList(),
)
