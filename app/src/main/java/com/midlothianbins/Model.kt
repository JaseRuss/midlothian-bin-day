package com.midlothianbins

import java.time.LocalDate

enum class BinType(val code: String, val label: String, val short: String, val color: Int) {
    Blue("Bl", "Plastics, cans & cartons", "Plastics", 0xFF1976D2.toInt()),
    Red("Rd", "Glass bottles & jars", "Glass", 0xFFC62828.toInt()),
    Grey("Gy", "General waste", "General", 0xFF757575.toInt()),
    Green("Gn", "Card & paper", "Card/paper", 0xFF2E7D32.toInt()),
    Food("Fd", "Food caddy", "Food", 0xFF7B1FA2.toInt()),
    Brown("Br", "Garden waste", "Garden", 0xFF9C5329.toInt());

    companion object {
        fun fromCode(code: String): BinType? = values().firstOrNull { it.code == code }

        /** Splits a concatenated cell such as "FdBr" into its bins. */
        fun parseCell(cell: String): List<BinType>? {
            if (cell.isEmpty() || cell.length % 2 != 0) return null
            val bins = cell.chunked(2).map { fromCode(it) ?: return null }
            return bins.distinct()
        }
    }
}

data class PickUp(val date: LocalDate, val bins: List<BinType>) {
    val longNames: String get() = bins.joinToString(" + ") { it.label }
    val shortNames: String get() = bins.joinToString(" + ") { it.short }
}

data class ScheduleData(val pickups: List<PickUp>)
