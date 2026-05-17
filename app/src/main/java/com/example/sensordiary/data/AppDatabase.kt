package com.example.sensordiary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sensordiary.model.MoodRecord

@Database(entities = [MoodRecord::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mood_records ADD COLUMN activityState TEXT NOT NULL DEFAULT '未知'")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN confidenceScore REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN lightValue INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN dbValue INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN shakeValue REAL NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mood_records ADD COLUMN voicePitch REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN voiceTone TEXT NOT NULL DEFAULT '未知'")
                db.execSQL("ALTER TABLE mood_records ADD COLUMN voiceContent TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sensor_diary_db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
