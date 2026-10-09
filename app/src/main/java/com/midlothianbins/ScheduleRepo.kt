package com.midlothianbins

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * Holds the collection schedule. An imported schedule (saved to app storage)
 * takes priority over the one bundled from the original PDF.
 */
object ScheduleRepo {
    private const val FILE_NAME = "schedule.txt"

    @Volatile
    private var cache: ScheduleData? = null

    fun get(ctx: Context): ScheduleData {
        cache?.let { return it }
        val file = File(ctx.filesDir, FILE_NAME)
        val stored = if (file.exists()) runCatching { parseStored(file.readText()) }.getOrNull() else null
        return (stored ?: bundled()).also { cache = it }
    }

    fun save(ctx: Context, data: ScheduleData) {
        val text = buildString {
            data.pickups.forEach { p ->
                appendLine("${p.date}|${p.bins.joinToString(",") { it.code }}")
            }
        }
        File(ctx.filesDir, FILE_NAME).writeText(text)
        cache = data
    }

    /** Removes all collection data (e.g. the bundled schedule is for a different address). */
    fun clear(ctx: Context) = save(ctx, ScheduleData(emptyList()))

    fun upcoming(ctx: Context, from: LocalDate = LocalDate.now()): List<PickUp> =
        get(ctx).pickups.filter { !it.date.isBefore(from) }

    fun on(ctx: Context, date: LocalDate): PickUp? = get(ctx).pickups.firstOrNull { it.date == date }

    private fun parseStored(text: String): ScheduleData? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ScheduleData(emptyList())
        val pickups = lines.map { line ->
            val (d, codes) = line.split("|")
            PickUp(LocalDate.parse(d), codes.split(",").map { BinType.fromCode(it)!! })
        }
        return ScheduleData(pickups)
    }

    private fun bundled(): ScheduleData {
        val pickups = mutableListOf<PickUp>()
        BUNDLED_WEEKS.lines().filter { it.isNotBlank() }.forEach { line ->
            val (monday, mon, tue) = line.trim().split("|")
            val d = LocalDate.parse(monday)
            pickups += PickUp(d, mon.split(",").map { BinType.fromCode(it)!! })
            pickups += PickUp(d.plusDays(1), tue.split(",").map { BinType.fromCode(it)!! })
        }
        return ScheduleData(pickups)
    }

    // Monday date | Monday bins | Tuesday bins  (from the council PDF printed 09/10/2026)
    private const val BUNDLED_WEEKS = """
2026-10-12|Fd,Br|Gn
2026-10-19|Fd,Rd|Gy
2026-10-26|Fd,Br|Bl
2026-11-02|Fd,Rd|Gn
2026-11-09|Fd,Br|Gy
2026-11-16|Fd,Rd|Bl
2026-11-23|Fd|Gn
2026-11-30|Fd,Rd|Gy
2026-12-07|Fd|Bl
2026-12-14|Fd,Rd|Gn
2026-12-21|Fd|Gy
2026-12-28|Fd,Rd|Bl
2027-01-04|Fd|Gn
2027-01-11|Fd,Rd|Gy
2027-01-18|Fd|Bl
2027-01-25|Fd,Rd|Gn
2027-02-01|Fd|Gy
2027-02-08|Fd,Rd|Bl
2027-02-15|Fd|Gn
2027-02-22|Fd,Rd|Gy
2027-03-01|Fd|Bl
2027-03-08|Fd,Rd|Gn
2027-03-15|Fd|Gy
2027-03-22|Fd,Rd|Bl
2027-03-29|Fd|Gn
2027-04-05|Fd,Rd|Gy
2027-04-12|Fd|Bl
2027-04-19|Fd,Rd|Gn
2027-04-26|Fd|Gy
2027-05-03|Fd,Rd|Bl
2027-05-10|Fd|Gn
2027-05-17|Fd,Rd|Gy
2027-05-24|Fd|Bl
2027-05-31|Fd,Rd|Gn
2027-06-07|Fd|Gy
2027-06-14|Fd,Rd|Bl
2027-06-21|Fd|Gn
2027-06-28|Fd,Rd|Gy
2027-07-05|Fd|Bl
2027-07-12|Fd,Rd|Gn
2027-07-19|Fd|Gy
2027-07-26|Fd,Rd|Bl
2027-08-02|Fd|Gn
2027-08-09|Fd,Rd|Gy
2027-08-16|Fd|Bl
2027-08-23|Fd,Rd|Gn
2027-08-30|Fd|Gy
2027-09-06|Fd,Rd|Bl
2027-09-13|Fd|Gn
2027-09-20|Fd,Rd|Gy
2027-09-27|Fd|Bl
"""
}
