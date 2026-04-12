package jp.co.terumo.tracelink.rp902app.domain.inventory

import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.upload.PendingUpload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState
import kotlinx.coroutines.flow.StateFlow

interface InventoryRepository : AutoCloseable {
    val state: StateFlow<InventoryRepositoryState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun startInventory()
    suspend fun stopInventory()
    suspend fun clearSession()
    suspend fun uploadSession()
    suspend fun retryPendingUploads()

    override fun close() = Unit
}

data class InventoryRepositoryState(
    val connectionState: ReaderConnectionState = ReaderConnectionState.Disconnected,
    val isInventoryRunning: Boolean = false,
    val tags: List<InventoryTag> = emptyList(),
    val uploadState: UploadState = UploadState.Idle,
    val pendingUploads: List<PendingUpload> = emptyList(),
    val logs: List<AppLogEntry> = emptyList(),
)
