package jp.co.terumo.tracelink.rp902app.data.postgres

import java.sql.Array as SqlArray
import java.sql.ResultSet
import java.sql.Timestamp

internal data class PostgresRow(
    private val values: Map<String, Any?>,
) {
    fun requiredString(column: String): String =
        requireNotNull(values[column]) { "Required column '$column' was null." }.toString()

    fun optionalString(column: String): String? = values[column]?.toString()

    fun requiredLong(column: String): Long {
        val value = requireNotNull(values[column]) { "Required column '$column' was null." }
        return when (value) {
            is Number -> value.toLong()
            is Timestamp -> value.time
            is String -> value.toLong()
            else -> error("Column '$column' cannot be converted to Long: ${value::class.java.name}")
        }
    }

    fun requiredBoolean(column: String): Boolean {
        val value = requireNotNull(values[column]) { "Required column '$column' was null." }
        return when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrict()
            else -> error("Column '$column' cannot be converted to Boolean: ${value::class.java.name}")
        }
    }

    fun optionalBoolean(column: String): Boolean? {
        val value = values[column] ?: return null
        return when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrict()
            else -> error("Column '$column' cannot be converted to Boolean: ${value::class.java.name}")
        }
    }

    fun requiredStringSet(column: String): Set<String> {
        val value = requireNotNull(values[column]) { "Required column '$column' was null." }
        return when (value) {
            is SqlArray -> value.array.toArrayValue(column).toStringSet(column)
            is Array<*> -> value.toStringSet(column)
            is Iterable<*> -> value.mapToStringSet(column)
            is String -> value.split(",").mapToStringSet(column)
            else -> error("Column '$column' cannot be converted to Set<String>: ${value::class.java.name}")
        }
    }
}

internal fun ResultSet.toPostgresRow(columns: List<String>): PostgresRow =
    PostgresRow(
        values = columns.associateWith { column -> getObject(column) },
    )

private fun Array<*>.toStringSet(column: String): Set<String> = asIterable().mapToStringSet(column)

private fun Any.toArrayValue(column: String): Array<*> =
    this as? Array<*>
        ?: error("Column '$column' SQL array did not expose an object array.")

private fun Iterable<*>.mapToStringSet(column: String): Set<String> =
    mapNotNull { item -> item?.toString()?.trim()?.takeIf(String::isNotBlank) }
        .toSet()
        .also { values ->
            require(values.isNotEmpty()) { "Column '$column' must contain at least one value." }
        }
