package org.gallery.swiper.data.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val thumbnailUri: Uri = uri,
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
