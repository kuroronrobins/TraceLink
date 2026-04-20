package jp.co.terumo.tracelink.rp902app.data.postgres

import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultTag

internal object ReadResultBundleJsonEncoder {
    fun encode(bundle: ReadResultRegistrationBundle): String =
        buildString {
            append("{")
            appendStringField("sessionId", bundle.sessionId)
            append(",")
            appendNumberField("registeredAtEpochMillis", bundle.registeredAtEpochMillis)
            append(",")
            appendStringField("deviceId", bundle.deviceId)
            append(",")
            appendStringField("readerType", bundle.readerType)
            append(",")
            appendStringField("workId", bundle.workId)
            append(",")
            appendStringField("reportId", bundle.reportId)
            append(",")
            appendNullableStringField("operatorId", bundle.operatorId)
            append(",")
            appendStringField("ruleVersion", bundle.ruleVersion)
            append(",")
            appendStringField("equipmentSnapshotVersion", bundle.equipmentSnapshotVersion)
            append(",")
            append("\"tags\":[")
            bundle.tags.forEachIndexed { index, tag ->
                if (index > 0) append(",")
                appendTag(tag)
            }
            append("]")
            append("}")
        }

    private fun StringBuilder.appendTag(tag: ReadResultTag) {
        append("{")
        appendStringField("epc", tag.epc)
        append(",")
        appendNumberField("firstSeenAtEpochMillis", tag.firstSeenAtEpochMillis)
        append(",")
        appendNumberField("lastSeenAtEpochMillis", tag.lastSeenAtEpochMillis)
        append(",")
        appendNumberField("readCount", tag.readCount.toLong())
        append(",")
        appendStringField("judgementStatus", tag.judgementStatus.name)
        append(",")
        appendNullableStringField("judgementReasonCode", tag.judgementReasonCode)
        append("}")
    }

    private fun StringBuilder.appendStringField(name: String, value: String) {
        append("\"")
        append(escape(name))
        append("\":\"")
        append(escape(value))
        append("\"")
    }

    private fun StringBuilder.appendNullableStringField(name: String, value: String?) {
        append("\"")
        append(escape(name))
        append("\":")
        if (value == null) {
            append("null")
        } else {
            append("\"")
            append(escape(value))
            append("\"")
        }
    }

    private fun StringBuilder.appendNumberField(name: String, value: Long) {
        append("\"")
        append(escape(name))
        append("\":")
        append(value)
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
}
