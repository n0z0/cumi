package id.biz.id.kaoskaki.proxy

import java.util.Locale

object Formatters {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        var v = value.toDouble()
        var unit = 0
        while (v >= 1024 && unit < units.lastIndex) {
            v /= 1024
            unit++
        }
        val pattern = when {
            v < 10 -> "%.2f %s"
            v < 100 -> "%.1f %s"
            else -> "%.0f %s"
        }
        return String.format(Locale.US, pattern, v, units[unit])
    }

    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"

    fun duration(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        else -> uptime(ms)
    }

    fun uptime(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
