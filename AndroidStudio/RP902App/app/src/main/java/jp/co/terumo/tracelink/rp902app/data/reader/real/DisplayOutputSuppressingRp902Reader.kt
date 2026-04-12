package jp.co.terumo.tracelink.rp902app.data.reader.real

import com.unitech.lib.diagnositics.ReaderException
import com.unitech.lib.reader.params.DisplayOutput
import com.unitech.lib.rpx.RP902Reader
import com.unitech.lib.transport.BaseTransport

/**
 * RP902Reader の `setDisplayOutput` だけを抑止する実機向け workaround。
 *
 * 実機ログで、inventory 開始後に vendor 内部 thread が `setDisplayOutput(DisplayOutput)` を呼び、
 * native JNI で crash する経路が確認された。アプリ側が明示的に呼ぶ処理ではないため、
 * vendor SDK 境界で抑止し、呼び出し有無は structured log に残す。
 *
 * SDK/FW 更新で DisplayOutput 経路が安全になった場合は、この class の必要性を再確認する。
 */
internal class DisplayOutputSuppressingRp902Reader(
    transport: BaseTransport,
    private val onDisplayOutputSuppressed: (DisplayOutput?) -> Unit,
) : RP902Reader(transport) {
    @Throws(ReaderException::class)
    override fun setDisplayOutput(displayOutput: DisplayOutput?) {
        onDisplayOutputSuppressed(displayOutput)
    }
}
