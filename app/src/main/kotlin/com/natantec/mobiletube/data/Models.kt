package com.natantec.mobiletube.data

import java.util.Locale

data class VideoItem(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val views: Long,
    val durationSec: Long,
    val thumb: String?,
    val uploaded: String?
) {
    /** Shorts: link /shorts/ ou vídeo de até 60 segundos. */
    val isShort: Boolean
        get() = url.contains("/shorts/") || durationSec in 1..60
}

fun formatDuration(totalSec: Long): String {
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatViews(n: Long): String {
    if (n < 0) return ""
    val br = Locale("pt", "BR")
    return when {
        n < 1_000 -> "$n visualizações"
        n < 1_000_000 -> String.format(br, "%.1f mil visualizações", n / 1_000.0)
        n < 1_000_000_000 -> String.format(br, "%.1f mi de visualizações", n / 1_000_000.0)
        else -> String.format(br, "%.1f bi de visualizações", n / 1_000_000_000.0)
    }
}
