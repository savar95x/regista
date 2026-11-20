package dev.savar.regista

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Times are always shown in the phone's time zone and 12/24-hour setting. */
object Fmt {
    var zone: ZoneId = ZoneId.systemDefault()

    private fun date(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    fun time(ctx: Context, t: Long): String {
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(t).atZone(zone))
    }

    /** "today", "tomorrow", "Saturday" (within a week) or "Sat 6 Dec". */
    fun dayWord(ctx: Context, t: Long, now: Long = System.currentTimeMillis()): String {
        val d = date(t)
        val days = ChronoUnit.DAYS.between(date(now), d)
        return when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days == -1L -> "yesterday"
            days in 2..6 -> DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(d)
            else -> DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(d)
        }
    }

    /** Section header for Up next: "Today", "Tomorrow", "Saturday 22 November". */
    fun dayHeader(t: Long, now: Long = System.currentTimeMillis()): String {
        val d = date(t)
        return when (ChronoUnit.DAYS.between(date(now), d)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(d)
        }
    }

    fun dayKey(t: Long): LocalDate = date(t)

    /** "in 3 h 20 min", "in 12 min", "now". */
    fun until(t: Long, now: Long = System.currentTimeMillis()): String {
        val min = (t - now) / 60_000
        return when {
            min <= 0 -> "now"
            min < 60 -> "in $min min"
            min < 24 * 60 -> "in ${min / 60} h" + if (min % 60 != 0L) " ${min % 60} min" else ""
            else -> "in ${min / (24 * 60)} d"
        }
    }

    /** Compact form for tight spots: "in 12h 15m", "in 8m". */
    fun untilShort(t: Long, now: Long = System.currentTimeMillis()): String {
        val min = (t - now) / 60_000
        return when {
            min <= 0 -> "now"
            min < 60 -> "in ${min}m"
            else -> "in ${min / 60}h" + if (min % 60 != 0L) " ${min % 60}m" else ""
        }
    }

    fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
        if (t <= 0) return "never"
        val min = (now - t) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} h ago"
            else -> "${min / (24 * 60)} d ago"
        }
    }
}
