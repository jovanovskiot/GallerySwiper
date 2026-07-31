package org.gallery.swiper.util

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    private var cachedLocale: Locale? = null
    private var cachedFormatter: DateTimeFormatter? = null

    private val formatter: DateTimeFormatter
        get() {
            val locale = Locale.getDefault()
            if (cachedLocale != locale) {
                cachedLocale = locale
                cachedFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
            }
            return cachedFormatter!!
        }

    fun formatMonthYear(year: Int, month: Int): String {
        return YearMonth.of(year, month).format(formatter)
    }
}
