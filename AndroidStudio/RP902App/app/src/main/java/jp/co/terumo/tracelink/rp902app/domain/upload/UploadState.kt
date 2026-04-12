package jp.co.terumo.tracelink.rp902app.domain.upload

sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Completed(val sessionId: String) : UploadState
    data class Failed(val message: String) : UploadState
}
