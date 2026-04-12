package jp.co.terumo.tracelink.rp902app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import jp.co.terumo.tracelink.rp902app.ui.theme.TraceLink_RP902AppTheme

/**
 * structured log を一覧表示する画面。
 *
 * ログ生成は Repository/Gateway が担当し、この Composable は `AppLogEntry` を表示するだけ。
 * 実機デバッグでは Reader category のログから callback 到達有無を追う。
 */
@Composable
fun LogsScreen(
    logs: List<AppLogEntry>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    text = "No events yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(logs) { log ->
                Text(
                    text = log.displayText(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
private fun LogsScreenPreview() {
    TraceLink_RP902AppTheme {
        LogsScreen(
            logs = listOf(
                AppLogEntry(
                    id = 1L,
                    occurredAtEpochMillis = 1000L,
                    level = AppLogLevel.Info,
                    category = AppLogCategory.System,
                    message = "Session initialized.",
                ),
                AppLogEntry(
                    id = 2L,
                    occurredAtEpochMillis = 1200L,
                    level = AppLogLevel.Info,
                    category = AppLogCategory.Reader,
                    message = "Reader: Connected",
                ),
            ),
        )
    }
}
