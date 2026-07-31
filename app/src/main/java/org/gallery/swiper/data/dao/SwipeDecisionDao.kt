package org.gallery.swiper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.gallery.swiper.data.entity.SwipeDecisionEntity

@Dao
interface SwipeDecisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decision: SwipeDecisionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(decisions: List<SwipeDecisionEntity>)

    @Query("SELECT * FROM swipe_decisions WHERE monthKey = :monthKey AND isCommitted = 0")
    suspend fun getPendingDecisions(monthKey: String): List<SwipeDecisionEntity>

    @Query("SELECT * FROM swipe_decisions WHERE monthKey = :monthKey AND isCommitted = 1")
    suspend fun getCommittedDecisions(monthKey: String): List<SwipeDecisionEntity>

    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE monthKey = :monthKey AND isCommitted = 0")
    suspend fun getPendingCount(monthKey: String): Int

    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE monthKey = :monthKey AND decision = 'DELETE' AND isCommitted = 0")
    suspend fun getPendingDeleteCount(monthKey: String): Int

    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE monthKey = :monthKey AND decision = 'KEEP' AND isCommitted = 0")
    suspend fun getPendingKeepCount(monthKey: String): Int

    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE monthKey = :monthKey AND isCommitted = 1")
    suspend fun getCommittedCount(monthKey: String): Int

    @Query("SELECT COUNT(DISTINCT monthKey) FROM swipe_decisions WHERE isCommitted = 1")
    suspend fun getCompletedMonthCount(): Int

    @Query("SELECT monthKey, decision FROM swipe_decisions WHERE isCommitted = 0")
    suspend fun getAllPendingFlat(): List<PendingRow>

    @Query("SELECT monthKey, COUNT(*) as cnt FROM swipe_decisions WHERE isCommitted = 1 GROUP BY monthKey")
    suspend fun getCommittedCountsPerMonth(): List<MonthCount>

    data class PendingRow(val monthKey: String, val decision: String)
    data class MonthCount(val monthKey: String, val cnt: Int)

    @Query("DELETE FROM swipe_decisions WHERE monthKey = :monthKey")
    suspend fun deleteMonth(monthKey: String)

    @Query("DELETE FROM swipe_decisions WHERE photoId = :photoId AND monthKey = :monthKey AND isCommitted = 0")
    suspend fun removeDecision(photoId: Long, monthKey: String)

    @Query("UPDATE swipe_decisions SET decision = :decision WHERE photoId = :photoId AND isCommitted = 1")
    suspend fun updateCommittedDecision(photoId: Long, decision: String)

    @Transaction
    suspend fun replaceMonth(monthKey: String, finalDecisions: List<SwipeDecisionEntity>) {
        deleteMonth(monthKey)
        if (finalDecisions.isNotEmpty()) {
            upsertAll(finalDecisions)
        }
    }
}
