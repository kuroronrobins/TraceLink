package jp.co.terumo.tracelink.rp902app.data.reader.real

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead

/**
 * vendor tag callback の raw payload を app-owned な `ReaderTagRead` へ変換する mapper。
 *
 * callback 引数は実機や SDK 状態によって null/空/想定外になる可能性があるため、
 * 例外で落とさず、成功/失敗と診断メッセージを `Rp902TagEventMapping` として返す。
 */
internal data class Rp902TagEventMapping(
    val read: ReaderTagRead?,
    val diagnosticMessage: String,
    val failureMessage: String? = null,
)

internal object Rp902TagEventMapper {
    /**
     * `rawTag` は vendor sample で EPC として扱われている文字列。
     * ここでは vendor 型を domain へ漏らさず、最小限の妥当性確認だけを行う。
     */
    fun map(
        rawTag: String?,
        params: Any?,
        seenAtEpochMillis: Long,
        callbackIndex: Long,
    ): Rp902TagEventMapping {
        val rawLength = rawTag?.length ?: 0
        val epc = rawTag?.trim().orEmpty()
        val diagnosticMessage = "Vendor tag callback #$callbackIndex: " +
            "rawPresent=${rawTag != null} rawLength=$rawLength " +
            "paramsType=${params?.javaClass?.name ?: "null"}"

        if (epc.isBlank()) {
            return Rp902TagEventMapping(
                read = null,
                diagnosticMessage = diagnosticMessage,
                failureMessage = "RP902 tag event ignored: EPC is blank.",
            )
        }

        if (epc.length > MaxEpcLength) {
            return Rp902TagEventMapping(
                read = null,
                diagnosticMessage = diagnosticMessage,
                failureMessage = "RP902 tag event ignored: EPC is too long (${epc.length}).",
            )
        }

        return Rp902TagEventMapping(
            read = ReaderTagRead(
                epc = epc,
                seenAtEpochMillis = seenAtEpochMillis,
            ),
            diagnosticMessage = "$diagnosticMessage epcLength=${epc.length}",
        )
    }

    private const val MaxEpcLength = 512
}
