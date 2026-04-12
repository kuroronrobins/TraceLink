package jp.co.terumo.tracelink.rp902app.data.upload

import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRepository
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadResult
import kotlinx.coroutines.delay

/**
 * backend 未接続期間の fake upload transport。
 *
 * 本物サーバー接続時はこの class を改造するのではなく、`UploadRepository` の別実装を追加して
 * `AppContainer` で差し替える。Repository 側の retry queue 動作を実機や network なしで確認できる。
 */
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
