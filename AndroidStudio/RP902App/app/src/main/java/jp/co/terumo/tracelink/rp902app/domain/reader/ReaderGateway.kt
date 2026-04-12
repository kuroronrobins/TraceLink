package jp.co.terumo.tracelink.rp902app.domain.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * reader への app-owned な入口。
 *
 * Repository はこの interface だけを使うため、fake reader と real RP902 を同じ形で扱える。
 * vendor SDK の型や callback はこの境界の内側で `ReaderTagRead` や `ReaderGatewayEvent` に
 * 変換し、UI/domain へ漏らさない。
 */
interface ReaderGateway : AutoCloseable {
    /** 接続状態。画面表示と Start ボタン有効化の元になる。 */
    val connectionState: StateFlow<ReaderConnectionState>

    /** タグが 1 回読めるたびに流れるイベント。重複除去は Repository/Session 側で行う。 */
    val tagReads: Flow<ReaderTagRead>

    /** 実機調査や preflight のための診断イベント。通常処理の成否判断には使わない。 */
    val events: Flow<ReaderGatewayEvent>
        get() = emptyFlow()

    suspend fun connect()
    suspend fun disconnect()
    suspend fun startInventory()
    suspend fun stopInventory()

    override fun close() = Unit
}
