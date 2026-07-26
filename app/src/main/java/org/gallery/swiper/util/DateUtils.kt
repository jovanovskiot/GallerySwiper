package org.gallery.swiper.util

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    fun formatMonthYear(year: Int, month: Int): String {
        val yearMonth = YearMonth.of(year, month)
        return yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }
}
