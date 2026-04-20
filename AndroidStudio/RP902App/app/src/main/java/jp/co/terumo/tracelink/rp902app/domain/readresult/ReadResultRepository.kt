package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * 端末内で判定済みの読取結果を保存する契約。
 *
 * 実接続では PostgreSQL function を呼ぶ想定だが、UI と ViewModel はその詳細を知らない。
 * table 直叩きや SQL 組み立ては data 実装のさらに内側へ閉じ込める。
 */
interface ReadResultRepository {
    suspend fun register(bundle: ReadResultRegistrationBundle): ReadResultRegistrationResult
}

sealed interface ReadResultRegistrationResult {
    data class Success(
        val duplicate: Boolean = false,
        val acceptedSessionId: String? = null,
        val resultId: String? = null,
    ) : ReadResultRegistrationResult

    data class Failure(
        val message: String,
        val kind: RegistrationFailureKind = RegistrationFailureKind.Retryable,
        val errorCode: String? = null,
    ) : ReadResultRegistrationResult
}

enum class RegistrationFailureKind {
    Retryable,
    Configuration,
    Contract,
    Unknown,
}
