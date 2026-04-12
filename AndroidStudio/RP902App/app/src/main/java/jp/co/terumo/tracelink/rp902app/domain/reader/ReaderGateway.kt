package jp.co.terumo.tracelink.rp902app.domain.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ReaderGateway : AutoCloseable {
    val connectionState: StateFlow<ReaderConnectionState>
    val tagReads: Flow<ReaderTagRead>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun startInventory()
    suspend fun stopInventory()

    override fun close() = Unit
}

