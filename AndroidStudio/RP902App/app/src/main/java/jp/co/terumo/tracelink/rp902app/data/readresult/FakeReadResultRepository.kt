package jp.co.terumo.tracelink.rp902app.data.readresult

import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import kotlinx.coroutines.delay

/**
 * PostgreSQL function 未接続期間の fake 読取結果登録先。
 *
 * 実 DB 接続時はこの class を改造せず、`ReadResultRepository` の別実装を追加して
 * `AppContainer` で差し替える。Repository 側の pending write 動作を実機や DB なしで確認できる。
 */
class FakeReadResultRepository : ReadResultRepository {
    override suspend fun register(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult {
        delay(350L)
        return if (bundle.tags.isEmpty()) {
            ReadResultRegistrationResult.Failure("No tags to register.")
        } else {
            ReadResultRegistrationResult.Success
        }
    }
}
