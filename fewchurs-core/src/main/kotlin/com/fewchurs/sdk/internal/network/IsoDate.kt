package com.fewchurs.sdk.internal.network

/**
 * Parses the server's `toISOString()` timestamps (e.g. `2026-09-18T10:15:30.123Z`) without
 * java.time, which would need desugaring on API 23–25. Returns 0 for anything unparseable.
 */
internal object IsoDate {
    private val PATTERN = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:?\\d{2})?$",
    )

    fun parse(value: String?): Long {
        val m = value?.let { PATTERN.matchEntire(it.trim()) } ?: return 0L
        val g = m.groupValues
        val days = daysFromCivil(g[1].toLong(), g[2].toInt(), g[3].toInt())
        var millis = ((days * 24 + g[4].toLong()) * 60 + g[5].toLong()) * 60_000 + g[6].toLong() * 1000
        if (g[7].isNotEmpty()) millis += g[7].padEnd(3, '0').take(3).toLong()
        val zone = g[8]
        if (zone.isNotEmpty() && zone != "Z") {
            val sign = if (zone[0] == '-') -1 else 1
            val digits = zone.substring(1).replace(":", "")
            val offsetMinutes = digits.take(2).toLong() * 60 + digits.drop(2).toLong()
            millis -= sign * offsetMinutes * 60_000
        }
        return millis
    }

    // Howard Hinnant's days_from_civil: days since 1970-01-01 for a proleptic Gregorian date.
    private fun daysFromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
