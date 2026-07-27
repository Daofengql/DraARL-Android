package cn.silverdragon.draarl.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LogbookTime {
    private const val PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun nowLocal(): String = formatter(TimeZone.getDefault()).format(Date())

    fun localToUtc(value: String): String {
        val parsed = formatter(TimeZone.getDefault()).parse(value)
            ?: throw IllegalArgumentException("时间格式应为 YYYY-MM-DD HH:mm:ss")
        return formatter(TimeZone.getTimeZone("UTC")).format(parsed)
    }

    fun utcToLocal(value: String): String {
        val parsed = formatter(TimeZone.getTimeZone("UTC")).parse(value) ?: return value
        return formatter(TimeZone.getDefault()).format(parsed)
    }

    private fun formatter(zone: TimeZone) = SimpleDateFormat(PATTERN, Locale.CHINA).apply {
        isLenient = false
        timeZone = zone
    }
}
