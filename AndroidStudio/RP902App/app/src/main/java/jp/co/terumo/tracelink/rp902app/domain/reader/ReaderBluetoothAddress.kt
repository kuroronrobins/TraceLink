package jp.co.terumo.tracelink.rp902app.domain.reader

import java.util.Locale

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
