package org.gallery.swiper.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "swipe_decisions",
    indices = [Index(value = ["photoId", "monthKey"], unique = true)],
)
data class SwipeDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monthKey: String,
    val photoId: Long,
    val uri: String,
    val decision: String,
    val size: Long = 0,
    val mimeType: String = "image/*",
    val isCommitted: Boolean = false,
)
