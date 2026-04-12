package jp.co.terumo.tracelink.rp902app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode

/**
 * reader 設定画面。
 *
 * fake/real の選択、RP902 Bluetooth MAC、runtime permission と Bluetooth 状態の
 * preflight 結果を表示する。ここで実際の接続は行わず、設定値の変更だけを ViewModel に返す。
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onGatewayModeChange: (ReaderGatewayMode) -> Unit,
    onReaderAddressChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onRefreshPreflight: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Reader mode",
            style = MaterialTheme.typography.titleMedium,
        )
        GatewayModeRow(
            label = "Fake reader",
            selected = uiState.gatewayMode == ReaderGatewayMode.Fake,
            onClick = { onGatewayModeChange(ReaderGatewayMode.Fake) },
        )
        GatewayModeRow(
            label = "Real RP902",
            selected = uiState.gatewayMode == ReaderGatewayMode.RealRp902,
            onClick = { onGatewayModeChange(ReaderGatewayMode.RealRp902) },
        )

        OutlinedTextField(
            value = uiState.readerAddressInput,
            onValueChange = onReaderAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("RP902 Bluetooth MAC") },
            singleLine = true,
            isError = uiState.readerAddressError != null,
            supportingText = {
                Text(
                    text = uiState.readerAddressError
                        ?: "Example: 00:11:22:33:44:55",
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
            ),
        )

        if (uiState.requiredPermissions.isNotEmpty()) {
            PreflightSection(
                uiState = uiState,
                onRequestPermissions = onRequestPermissions,
                onRefreshPreflight = onRefreshPreflight,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
            )
        } else {
            Text(
                text = "Fake reader mode does not require Bluetooth preflight.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreflightSection(
    uiState: SettingsUiState,
    onRequestPermissions: () -> Unit,
    onRefreshPreflight: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(8.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Real RP902 preflight",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (uiState.canAttemptConnection) {
                "Ready to attempt real RP902 connection."
            } else {
                "Connection attempt is blocked until the items below are fixed."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.canAttemptConnection) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = "Bluetooth: ${uiState.bluetoothState}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Required permissions: ${uiState.requiredPermissions.joinToString()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Missing permissions: ${uiState.missingPermissions.joinToString().ifBlank { "None" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.missingPermissions.isEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (uiState.preflightFailures.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Blocking reasons",
                    style = MaterialTheme.typography.labelLarge,
                )
                uiState.preflightFailures.forEach { failure ->
                    Text(
                        text = "- $failure",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onRequestPermissions,
                enabled = uiState.missingManifestPermissions.isNotEmpty(),
                shape = buttonShape,
            ) {
                Text("Request permissions")
            }
            OutlinedButton(
                onClick = onRefreshPreflight,
                shape = buttonShape,
            ) {
                Text("Refresh")
            }
        }
        OutlinedButton(
            onClick = onOpenBluetoothSettings,
            shape = buttonShape,
        ) {
            Text("Open Bluetooth settings")
        }
    }
}

@Composable
private fun GatewayModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
