package com.alpha.screentranslator.data

import android.content.Context
import androidx.room.*
import com.alpha.screentranslator.model.TranslationHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TranslationHistoryItem>>

    @Query("SELECT * FROM translation_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<TranslationHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TranslationHistoryItem)

    @Update
    suspend fun update(item: TranslationHistoryItem)

    @Delete
    suspend fun delete(item: TranslationHistoryItem)

    @Query("DELETE FROM translation_history WHERE isFavorite = 0")
    suspend fun clearNonFavorites()
}

@Database(entities = [TranslationHistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alpha_translator_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
