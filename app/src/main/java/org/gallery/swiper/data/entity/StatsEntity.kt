@file:Suppress("Unused")

package org.gallery.swiper.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val id: Int = 1,
    val totalReviewed: Int = 0,
    val totalDeleted: Int = 0,
    val totalKept: Int = 0,
    val totalSpaceSaved: Long = 0L,
    val reviewStreak: Int = 0,
    val lastReviewDate: Long? = null,
)
