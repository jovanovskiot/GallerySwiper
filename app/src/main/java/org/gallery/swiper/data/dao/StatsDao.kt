package org.gallery.swiper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.gallery.swiper.data.entity.StatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initIfEmpty(stats: StatsEntity)

    @Query("SELECT * FROM stats WHERE id = 1")
    fun getStats(): Flow<StatsEntity?>

    @Query("UPDATE stats SET totalReviewed = totalReviewed + :delta WHERE id = 1")
    suspend fun addReviewed(delta: Int)

    @Query("UPDATE stats SET totalDeleted = totalDeleted + :delta, totalSpaceSaved = totalSpaceSaved + :spaceDelta WHERE id = 1")
    suspend fun addDeleted(delta: Int, spaceDelta: Long)

    @Query("UPDATE stats SET totalKept = totalKept + :delta WHERE id = 1")
    suspend fun addKept(delta: Int)

    @Query("UPDATE stats SET reviewStreak = reviewStreak + 1, lastReviewDate = :today WHERE id = 1")
    suspend fun incrementStreak(today: Long)

    @Query("UPDATE stats SET reviewStreak = 1, lastReviewDate = :today WHERE id = 1")
    suspend fun resetStreak(today: Long)

    @Query("SELECT lastReviewDate FROM stats WHERE id = 1")
    suspend fun getLastReviewDate(): Long?
}
