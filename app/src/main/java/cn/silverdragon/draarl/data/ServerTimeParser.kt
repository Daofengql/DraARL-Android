package cn.silverdragon.draarl.data

import java.text.SimpleDateFormat
import java.util.Locale

internal object ServerTimeParser {
    private val rfc3339 = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}:\\d{2})$")

    fun parseMillis(value: String): Long? {
        val trimmed = value.trim()
        rfc3339.matchEntire(trimmed)?.let { match ->
            val milliseconds = match.groupValues[2].take(3).padEnd(3, '0')
            val normalized = "${match.groupValues[1]}.$milliseconds${match.groupValues[3]}"
            return parse(normalized, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        }
        return parse(trimmed, "yyyy-MM-dd HH:mm:ss")
    }

    private fun parse(value: String, pattern: String): Long? = runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)?.time
    }.getOrNull()
}
