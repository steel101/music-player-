package com.steel101.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MetadataEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        SearchHistoryEntity::class,
        ExcludedFolderEntity::class
    ], 
    version = 9
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun metadataDao(): MetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
