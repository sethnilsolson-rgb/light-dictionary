package com.thelightphone.dictionary.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    // Offline Dictionary
    @Query("SELECT * FROM offline_words WHERE word = :word LIMIT 1")
    suspend fun getOfflineWord(word: String): OfflineWordEntity?

    @Query("SELECT word FROM offline_words WHERE word LIKE :prefix ORDER BY length(word), word LIMIT :limit")
    suspend fun searchOfflineWords(prefix: String, limit: Int = 25): List<String>

    @Query("SELECT word FROM offline_words WHERE word LIKE :charPrefix AND length(word) BETWEEN :minLen AND :maxLen LIMIT 500")
    suspend fun getCandidateWords(charPrefix: String, minLen: Int, maxLen: Int): List<String>

    @Query("SELECT word FROM offline_words WHERE length(word) BETWEEN :minLen AND :maxLen LIMIT 1000")
    suspend fun getFallbackCandidateWords(minLen: Int, maxLen: Int): List<String>

    // Cached Online Words
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
