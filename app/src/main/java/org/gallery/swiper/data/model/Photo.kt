package org.gallery.swiper.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Photo(
    val id: Long,
    val uri: Uri,
    val thumbnailUri: Uri = uri,
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
