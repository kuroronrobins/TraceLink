package jp.co.terumo.tracelink.rp902app.domain.upload

interface UploadRepository {
    suspend fun upload(payload: InventoryUploadPayload): UploadResult
}

sealed interface UploadResult {
    data object Success : UploadResult
    data class Failure(val message: String) : UploadResult
}
