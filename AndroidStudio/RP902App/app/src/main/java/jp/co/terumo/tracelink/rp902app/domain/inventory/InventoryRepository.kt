package jp.co.terumo.tracelink.rp902app.domain.inventory

import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.upload.PendingUpload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState
import kotlinx.coroutines.flow.StateFlow

/**
 * Inventory 機能を ViewModel へ公開する契約。
 *
 * 画面操作に対応する関数と、画面が表示するための `state` をまとめる。
 * 実装側では reader、session、upload、retry、log を組み合わせるが、
 * UI からはその詳細を見せない。
 */
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

/**
 * Inventory 画面と Logs 画面の source of truth になる repository state。
 *
 * ViewModel はこの state を `InventoryUiState` に変換するだけにし、
 * UI が独自に接続状態や upload 状態を持たないようにする。
 */
data class InventoryRepositoryState(
    val connectionState: ReaderConnectionState = ReaderConnectionState.Disconnected,
    val isInventoryRunning: Boolean = false,
    val tags: List<InventoryTag> = emptyList(),
    val uploadState: UploadState = UploadState.Idle,
    val pendingUploads: List<PendingUpload> = emptyList(),
    val logs: List<AppLogEntry> = emptyList(),
)
