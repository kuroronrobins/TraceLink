package jp.co.terumo.tracelink.rp902app.data.upload

import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRepository
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadResult
import kotlinx.coroutines.delay

class FakeUploadRepository : UploadRepository {
    override suspend fun upload(payload: InventoryUploadPayload): UploadResult {
        delay(350L)
        return if (payload.tags.isEmpty()) {
            UploadResult.Failure("No tags to upload.")
        } else {
            UploadResult.Success
        }
    }
}

