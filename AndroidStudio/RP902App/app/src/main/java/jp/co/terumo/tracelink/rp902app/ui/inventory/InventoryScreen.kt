package jp.co.terumo.tracelink.rp902app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.reader.displayText
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState
import jp.co.terumo.tracelink.rp902app.ui.theme.TraceLink_RP902AppTheme

@Composable
fun InventoryRoute(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    InventoryScreen(
        uiState = uiState,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onStartInventory = viewModel::startInventory,
        onStopInventory = viewModel::stopInventory,
        onUpload = viewModel::uploadSession,
        onRetryPendingUploads = viewModel::retryPendingUploads,
        onClearSession = viewModel::clearSession,
        modifier = modifier,
    )
}

@Composable
fun InventoryScreen(
    uiState: InventoryUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStartInventory: () -> Unit,
    onStopInventory: () -> Unit,
    onUpload: () -> Unit,
    onRetryPendingUploads: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            StatusSection(uiState = uiState)
        }
        item {
            ActionSection(
                uiState = uiState,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onStartInventory = onStartInventory,
                onStopInventory = onStopInventory,
                onUpload = onUpload,
                onRetryPendingUploads = onRetryPendingUploads,
                onClearSession = onClearSession,
            )
        }
        item {
            SectionTitle("Tags")
        }
        if (uiState.tags.isEmpty()) {
            item {
                EmptyState()
            }
        } else {
            items(
                items = uiState.tags,
                key = { tag -> tag.epc },
            ) { tag ->
                TagRow(tag = tag)
            }
        }
        item {
            SectionTitle("Recent log")
        }
        items(uiState.logs.take(8)) { log ->
            LogRow(log = log)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(uiState: InventoryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Status")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                label = "Reader",
                value = uiState.connectionState.displayText(),
                color = connectionColor(uiState.connectionState),
            )
            StatusPill(
                label = "Inventory",
                value = if (uiState.isInventoryRunning) "Running" else "Stopped",
                color = if (uiState.isInventoryRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            StatusPill(
                label = "Unique tags",
                value = uiState.tags.size.toString(),
                color = MaterialTheme.colorScheme.tertiary,
            )
            StatusPill(
                label = "Upload",
                value = uiState.uploadState.displayText(),
                color = uploadColor(uiState.uploadState),
            )
            StatusPill(
                label = "Queued",
                value = uiState.pendingUploadCount.toString(),
                color = if (uiState.pendingUploadCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionSection(
    uiState: InventoryUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStartInventory: () -> Unit,
    onStopInventory: () -> Unit,
    onUpload: () -> Unit,
    onRetryPendingUploads: () -> Unit,
    onClearSession: () -> Unit,
) {
    val connected = uiState.connectionState == ReaderConnectionState.Connected
    val uploading = uiState.uploadState == UploadState.Uploading

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Actions")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = "Connect",
                enabled = !connected && uiState.connectionState != ReaderConnectionState.Connecting,
                onClick = onConnect,
            )
            ActionButton(
                text = "Disconnect",
                enabled = connected,
                onClick = onDisconnect,
                secondary = true,
            )
            ActionButton(
                text = "Start",
                enabled = connected && !uiState.isInventoryRunning,
                onClick = onStartInventory,
            )
            ActionButton(
                text = "Stop",
                enabled = uiState.isInventoryRunning,
                onClick = onStopInventory,
                secondary = true,
            )
            ActionButton(
                text = "Upload",
                enabled = uiState.tags.isNotEmpty() && !uploading,
                onClick = onUpload,
            )
            ActionButton(
                text = "Retry",
                enabled = uiState.pendingUploadCount > 0 && !uploading,
                onClick = onRetryPendingUploads,
            )
            ActionButton(
                text = "Clear",
                enabled = uiState.tags.isNotEmpty() && !uiState.isInventoryRunning,
                onClick = onClearSession,
                secondary = true,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    if (secondary) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
        ) {
            Text(text)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
        ) {
            Text(text)
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    color: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = color, shape = RoundedCornerShape(5.dp)),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TagRow(tag: InventoryTag) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = tag.epc,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Reads: ${tag.readCount}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Last: ${tag.lastSeenAtEpochMillis}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connect the reader and start inventory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogRow(log: AppLogEntry) {
    Text(
        text = log.displayText(),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun connectionColor(connectionState: ReaderConnectionState): Color = when (connectionState) {
    ReaderConnectionState.Connected -> MaterialTheme.colorScheme.primary
    ReaderConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
    ReaderConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    is ReaderConnectionState.Error -> MaterialTheme.colorScheme.error
}

@Composable
private fun uploadColor(uploadState: UploadState): Color = when (uploadState) {
    UploadState.Idle -> MaterialTheme.colorScheme.outline
    UploadState.Uploading -> MaterialTheme.colorScheme.tertiary
    is UploadState.Completed -> MaterialTheme.colorScheme.primary
    is UploadState.Failed -> MaterialTheme.colorScheme.error
}

private fun AppLogEntry.displayText(): String =
    "$occurredAtEpochMillis  ${level.displayText()} ${category.displayText()}: $message"

private fun AppLogLevel.displayText(): String = when (this) {
    AppLogLevel.Info -> "INFO"
    AppLogLevel.Warning -> "WARN"
    AppLogLevel.Error -> "ERROR"
}

private fun AppLogCategory.displayText(): String = when (this) {
    AppLogCategory.System -> "SYSTEM"
    AppLogCategory.Reader -> "READER"
    AppLogCategory.Inventory -> "INVENTORY"
    AppLogCategory.Upload -> "UPLOAD"
}

@Preview(showBackground = true)
@Composable
private fun InventoryScreenPreview() {
    TraceLink_RP902AppTheme {
        InventoryScreen(
            uiState = InventoryUiState(
                connectionState = ReaderConnectionState.Connected,
                tags = listOf(
                    InventoryTag(
                        epc = "E2806894000040035A1F90A1",
                        firstSeenAtEpochMillis = 1000L,
                        lastSeenAtEpochMillis = 2500L,
                        readCount = 2,
                    ),
                ),
                logs = listOf(
                    AppLogEntry(
                        id = 1L,
                        occurredAtEpochMillis = 1000L,
                        level = AppLogLevel.Info,
                        category = AppLogCategory.System,
                        message = "Session initialized.",
                    ),
                ),
            ),
            onConnect = {},
            onDisconnect = {},
            onStartInventory = {},
            onStopInventory = {},
            onUpload = {},
            onRetryPendingUploads = {},
            onClearSession = {},
        )
    }
}
