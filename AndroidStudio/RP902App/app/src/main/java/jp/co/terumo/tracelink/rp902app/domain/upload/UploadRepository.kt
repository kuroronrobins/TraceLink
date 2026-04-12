package jp.co.terumo.tracelink.rp902app.domain.upload

/**
 * backend upload transport の契約。
 *
 * Repository はこの interface だけを呼ぶ。HTTP client、認証、endpoint などの詳細は
 * 将来追加する data 実装に閉じ込める。
 */
interface UploadRepository {
    suspend fun upload(payload: InventoryUploadPayload): UploadResult
}

sealed interface UploadResult {
    data object Success : UploadResult
    data class Failure(val message: String) : UploadResult
}
