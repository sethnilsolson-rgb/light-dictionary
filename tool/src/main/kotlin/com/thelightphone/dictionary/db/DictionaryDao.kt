package com.thelightphone.dictionary.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM cached_words WHERE word = :word LIMIT 1")
    suspend fun getCachedWord(word: String): CachedWordEntity?

    @Query("SELECT word FROM cached_words ORDER BY word ASC")
    suspend fun getAllCachedWords(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWord(word: CachedWordEntity)

    // Bookmarks
    @Query("SELECT * FROM bookmarked_words ORDER BY bookmarkedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkWordEntity>>

    @Query("SELECT COUNT(*) FROM bookmarked_words")
    fun getBookmarkCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_words WHERE word = :word)")
    fun isWordBookmarked(word: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_words WHERE word = :word)")
    suspend fun isWordBookmarkedSync(word: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkWordEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkWordEntity)

    @Query("DELETE FROM bookmarked_words WHERE word = :word")
    suspend fun deleteBookmarkByWord(word: String)

    // Recent Searches
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE word = :word")
    suspend fun deleteRecentSearch(word: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()
}
