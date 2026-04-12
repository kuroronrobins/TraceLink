package jp.co.terumo.tracelink.rp902app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGatewayMode

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onGatewayModeChange: (ReaderGatewayMode) -> Unit,
    onReaderAddressChange: (String) -> Unit,
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
            Text(
                text = "Required permissions: ${uiState.requiredPermissions.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
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
