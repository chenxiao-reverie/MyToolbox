package com.toolbox.core.model

/** 支持 "90"、"1:30"、"00:01:30.5" 三种写法，解析失败返回 null。 */
object Timecode {

    fun parseToMs(text: String): Long? {
        val raw = text.trim()
        if (raw.isEmpty()) return null

        if (!raw.contains(":")) {
            return raw.toDoubleOrNull()?.let { (it * 1000).toLong() }
        }

        val parts = raw.split(":")
        if (parts.size !in 2..3) return null
        val secondsPart = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toIntOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toIntOrNull() ?: return null else 0

        val total = hours * 3600.0 + minutes * 60.0 + secondsPart
        return (total * 1000).toLong()
    }

    fun format(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
