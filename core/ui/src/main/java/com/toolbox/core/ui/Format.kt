package com.toolbox.core.ui

import java.util.Locale

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

fun formatPercent(fraction: Float): String = "${(fraction * 100).toInt()}%"