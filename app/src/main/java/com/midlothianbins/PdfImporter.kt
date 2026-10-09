package com.midlothianbins

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * Reads a "Bins collection schedule" PDF generated on my.midlothian.gov.uk.
 *
 * The extracted text lists each month under a "<Month> <Year>" heading, then the
 * calendar cells in reading order: a plain number is a day with no collection and a
 * bin code group (e.g. "FdBr") replaces the number on a collection day. So every
 * token is one calendar day, starting from the 1st of the month.
 */
object PdfImporter {
    private val monthHeading = Regex(
        "^(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})$",
        RegexOption.IGNORE_CASE
    )
    private val dayNumber = Regex("^\\d{1,2}$")
    private val weekdayNames = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun import(ctx: Context, uri: Uri): Result<ScheduleData> = runCatching {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        val text = ctx.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        }
        parse(text)
    }

    fun parse(text: String): ScheduleData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val pickups = mutableListOf<PickUp>()
        var month: YearMonth? = null
        var day = 1

        for (line in lines) {
            val heading = monthHeading.matchEntire(line)
            if (heading != null) {
                val m = Month.valueOf(heading.groupValues[1].uppercase())
                month = YearMonth.of(heading.groupValues[2].toInt(), m)
                day = 1
                continue
            }
            val ym = month ?: continue
            for (token in line.split(Regex("\\s+"))) {
                when {
                    token in weekdayNames -> Unit
                    dayNumber.matches(token) -> day = token.toInt() + 1
                    else -> {
                        val bins = BinType.parseCell(token) ?: continue
                        if (day <= ym.lengthOfMonth()) pickups += PickUp(ym.atDay(day), bins)
                        day++
                    }
                }
            }
        }

        if (pickups.isEmpty()) error("No collection dates found in this PDF.")
        return ScheduleData(pickups.distinctBy { it.date }.sortedBy { it.date })
    }
}
