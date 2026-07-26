package org.gallery.swiper.data.model

data class PhotoMonth(
    val year: Int,
    val month: Int,
    val photos: List<Photo>,
    val reviewedCount: Int = 0,
    val deletedCount: Int = 0,
    val keptCount: Int = 0,
) {
    val totalCount: Int get() = photos.size
    val progress: Float get() = if (totalCount > 0) reviewedCount.toFloat() / totalCount else 0f
    val key: String get() = "$year-${month.toString().padStart(2, '0')}"
    val label: String get() = monthName

    private val monthName: String
        get() = when (month) {
            1 -> "January"; 2 -> "February"; 3 -> "March"
            4 -> "April"; 5 -> "May"; 6 -> "June"
            7 -> "July"; 8 -> "August"; 9 -> "September"
            10 -> "October"; 11 -> "November"; 12 -> "December"
            else -> "Unknown"
        }
}
