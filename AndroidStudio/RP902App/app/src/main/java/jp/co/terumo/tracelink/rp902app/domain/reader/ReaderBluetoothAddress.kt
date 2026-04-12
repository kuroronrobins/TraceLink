package jp.co.terumo.tracelink.rp902app.domain.reader

import java.util.Locale

const val DEFAULT_RP902_BLUETOOTH_ADDRESS = "DC:0D:30:DA:0F:3C"

/**
 * RP902 接続先の Bluetooth MAC address を表す値オブジェクト。
 *
 * 入力欄では `00:11:22:33:44:55` と `001122334455` の両方を受け付け、
 * gateway へ渡す前にコロン区切り大文字へ正規化する。
 */
class ReaderBluetoothAddress private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ReaderBluetoothAddress && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val colonSeparatedPattern =
            Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
        private val compactPattern = Regex("^[0-9A-F]{12}$")

        /**
         * 今回運用で 1 台固定の RP902 に使う既定 MAC。
         *
         * gateway mode の既定は fake のままなので、実機接続は Settings で Real RP902 を
         * 選んだときだけ行う。必要なら Settings 画面から別 MAC へ編集できる。
         */
        val DefaultRp902: ReaderBluetoothAddress =
            ReaderBluetoothAddress(DEFAULT_RP902_BLUETOOTH_ADDRESS)

        /** 不正な MAC address は null にし、Settings 画面で入力エラーとして扱う。 */
        fun parse(rawValue: String): ReaderBluetoothAddress? {
            val candidate = rawValue.trim().uppercase(Locale.US)
            return when {
                colonSeparatedPattern.matches(candidate) -> ReaderBluetoothAddress(candidate)
                compactPattern.matches(candidate) -> ReaderBluetoothAddress(
                    candidate.chunked(2).joinToString(":"),
                )
                else -> null
            }
        }
    }
}
