package org.gallery.swiper.data

import androidx.room.TypeConverter
import org.gallery.swiper.data.model.Decision

class DecisionConverter {
    @TypeConverter
    fun fromDecision(value: Decision): String = value.name

    @TypeConverter
    fun toDecision(value: String): Decision =
        try { Decision.valueOf(value) } catch (_: IllegalArgumentException) { Decision.KEEP }
}
