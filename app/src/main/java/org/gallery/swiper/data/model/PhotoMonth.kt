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
}
