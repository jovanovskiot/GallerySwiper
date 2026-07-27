package org.gallery.swiper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.gallery.swiper.data.dao.BookmarkDao
import org.gallery.swiper.data.dao.StatsDao
import org.gallery.swiper.data.dao.SwipeDecisionDao
import org.gallery.swiper.data.entity.BookmarkEntity
import org.gallery.swiper.data.entity.StatsEntity
import org.gallery.swiper.data.entity.SwipeDecisionEntity

@Database(
    entities = [BookmarkEntity::class, StatsEntity::class, SwipeDecisionEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun statsDao(): StatsDao
    abstract fun swipeDecisionDao(): SwipeDecisionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun deduplicateAndCreateIndex(db: SupportSQLiteDatabase) {
            db.execSQL("""
                DELETE FROM swipe_decisions WHERE id NOT IN (
                    SELECT MAX(id) FROM swipe_decisions
                    WHERE isCommitted = 0
                    GROUP BY photoId, monthKey
                )
            """)
            db.execSQL("""
                DELETE FROM swipe_decisions WHERE id NOT IN (
                    SELECT MAX(id) FROM swipe_decisions
                    WHERE isCommitted = 1
                    GROUP BY photoId, monthKey
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_swipe_decisions_photo_month ON swipe_decisions(photoId, monthKey)")
        }

        private val MIGRATE_2_TO_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) { deduplicateAndCreateIndex(db) }
        }

        private val MIGRATE_4_TO_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE swipe_decisions ADD COLUMN mimeType TEXT NOT NULL DEFAULT 'image/*'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gallery_swiper.db"
                ).addMigrations(MIGRATE_2_TO_4, MIGRATE_4_TO_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
