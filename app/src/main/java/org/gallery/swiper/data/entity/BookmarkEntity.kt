package org.gallery.swiper.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val photoId: Long,
    val uri: String,
    val dateTaken: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
