package com.thelightphone.dictionary.data

import com.thelightphone.dictionary.db.BookmarkWordEntity
import com.thelightphone.dictionary.db.CachedWordEntity
import com.thelightphone.dictionary.db.DictionaryDatabase
import com.thelightphone.dictionary.db.RecentSearchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DictionaryRepository(
    private val database: DictionaryDatabase,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val bookmarks: Flow<List<BookmarkWordEntity>> = database.dictionaryDao().getAllBookmarks()
    val bookmarkCount: Flow<Int> = database.dictionaryDao().getBookmarkCount()
    val recentSearches: Flow<List<RecentSearchEntity>> = database.dictionaryDao().getRecentSearches()

    fun isWordBookmarked(word: String): Flow<Boolean> =
        database.dictionaryDao().isWordBookmarked(word.trim().lowercase())

    suspend fun toggleBookmark(entry: WordEntry) {
        val w = entry.word.trim().lowercase()
        val isBookmarked = database.dictionaryDao().isWordBookmarkedSync(w)
        if (isBookmarked) {
            database.dictionaryDao().deleteBookmarkByWord(w)
        } else {
            val preview = entry.meanings.firstOrNull()?.definitions?.firstOrNull()?.definition ?: ""
            val partOfSpeech = entry.meanings.firstOrNull()?.partOfSpeech ?: ""
            database.dictionaryDao().insertBookmark(
                BookmarkWordEntity(
                    word = w,
                    snippet = preview,
                    partOfSpeech = partOfSpeech,
                )
            )
        }
    }

    suspend fun deleteBookmark(bookmark: BookmarkWordEntity) {
        database.dictionaryDao().deleteBookmark(bookmark)
    }

    suspend fun recordRecentSearch(word: String) {
        if (word.isBlank()) return
        database.dictionaryDao().insertRecentSearch(
            RecentSearchEntity(word = word.trim().lowercase())
        )
    }

    suspend fun clearRecentSearches() {
        database.dictionaryDao().clearRecentSearches()
    }

    suspend fun getWord(word: String): WordEntry? = withContext(Dispatchers.IO) {
        val normalized = word.trim().lowercase()
        if (normalized.isBlank()) return@withContext null

        // 1. Check curated built-in dictionary
        val builtIn = BuiltInDictionary.find(normalized)
        if (builtIn != null) {
            recordRecentSearch(normalized)
            return@withContext builtIn
        }

        // 2. Check local database cache
        val cached = database.dictionaryDao().getCachedWord(normalized)
        if (cached != null) {
            val meanings = runCatching {
                json.decodeFromString<List<WordMeaning>>(cached.meaningsJson)
            }.getOrDefault(emptyList())

            recordRecentSearch(normalized)
            return@withContext WordEntry(
                word = cached.word,
                phonetic = cached.phonetic,
                origin = cached.origin,
                meanings = meanings,
            )
        }

        // 3. Check extensive offline dictionary (over 100,000 words, 0ms latency)
        val offline = database.dictionaryDao().getOfflineWord(normalized)
        if (offline != null) {
            recordRecentSearch(normalized)
            return@withContext WebsterParser.parse(offline.word, offline.definition)
        }

        // 4. Online live fetch fallback
        val result = DictionaryService.fetchWordOnline(normalized)
        val fetched = result.getOrNull()
        if (fetched != null) {
            val meaningsJson = json.encodeToString(fetched.meanings)
            database.dictionaryDao().insertCachedWord(
                CachedWordEntity(
                    word = fetched.word.lowercase(),
                    phonetic = fetched.phonetic,
                    origin = fetched.origin,
                    meaningsJson = meaningsJson,
                )
            )
            recordRecentSearch(normalized)
            return@withContext fetched
        }

        null
    }

    suspend fun getSuggestions(word: String): List<String> = withContext(Dispatchers.IO) {
        val q = word.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()

        val minLen = (q.length - 2).coerceAtLeast(1)
        val maxLen = q.length + 2
        val candidates = mutableSetOf<String>()

        val prefixes = mutableListOf<String>()
        if (q.length >= 4) prefixes.add(q.substring(0, 4))
        if (q.length >= 3) prefixes.add(q.substring(0, 3))
        prefixes.add(q.substring(0, 2))

        if (q.contains("f")) prefixes.add(q.replace("f", "ph").take(4))
        if (q.contains("ph")) prefixes.add(q.replace("ph", "f").take(3))

        for (p in prefixes) {
            candidates.addAll(database.dictionaryDao().getCandidateWords("$p%", minLen, maxLen))
        }

        val builtInWords = BuiltInDictionary.entries.map { it.word }
        candidates.addAll(builtInWords)

        candidates
            .map { Pair(it, WebsterParser.levenshteinDistance(q, it)) }
            .filter { it.second <= 3 }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .take(5)
            .map { it.first }
    }

    suspend fun isScrabbleLegal(word: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = word.trim().lowercase()
        if (normalized.length < 2 || normalized.any { !it.isLetter() }) return@withContext false
        database.dictionaryDao().isScrabbleLegal(normalized)
    }

    suspend fun getSimilarWords(word: String, limit: Int = 6): List<String> = withContext(Dispatchers.IO) {
        val q = word.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()

        val candidates = mutableSetOf<String>()
        candidates.addAll(database.dictionaryDao().getWordsStartingWith("$q%", 50))

        val minLen = (q.length - 2).coerceAtLeast(2)
        val maxLen = q.length + 4
        val prefixes = mutableListOf<String>()
        if (q.length >= 4) prefixes.add(q.substring(0, 4))
        if (q.length >= 3) prefixes.add(q.substring(0, 3))
        prefixes.add(q.substring(0, 2))

        for (p in prefixes) {
            candidates.addAll(database.dictionaryDao().getCandidateWords("$p%", minLen, maxLen))
        }

        candidates.remove(q)

        candidates
            .map { w ->
                val startsWithQuery = if (w.startsWith(q)) 0 else 1
                val dist = WebsterParser.levenshteinDistance(q, w)
                Pair(w, Triple(startsWithQuery, dist, w.length))
            }
            .filter { it.second.second <= 3 }
            .sortedWith(compareBy({ it.second.first }, { it.second.second }, { it.second.third }))
            .take(limit)
            .map { it.first }
    }

    suspend fun searchWords(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext emptyList()

        val builtInMatches = BuiltInDictionary.search(q)
        val cachedWords = database.dictionaryDao().getAllCachedWords()
        val cachedMatches = cachedWords.filter { it.contains(q) }
        val offlineMatches = database.dictionaryDao().searchOfflineWords("$q%", 25)

        (builtInMatches + cachedMatches + offlineMatches).distinct().sortedWith(
            compareBy<String> { !it.startsWith(q) }
                .thenBy { it.length }
                .thenBy { it }
        ).take(25)
    }

    suspend fun getAllKnownWordsAlphabetical(): List<String> = withContext(Dispatchers.IO) {
        val builtIn = BuiltInDictionary.getAlphabeticalIndex()
        val cached = database.dictionaryDao().getAllCachedWords()
        (builtIn + cached).distinct().sorted()
    }

    suspend fun getAdjacentWords(currentWord: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val list = getAllKnownWordsAlphabetical()
        val normalized = currentWord.trim().lowercase()
        val idx = list.indexOf(normalized)

        if (idx != -1) {
            val prev = if (idx > 0) list[idx - 1] else null
            val next = if (idx < list.size - 1) list[idx + 1] else null
            Pair(prev, next)
        } else {
            var insertionIdx = list.binarySearch(normalized)
            if (insertionIdx < 0) {
                insertionIdx = -(insertionIdx + 1)
            }
            val prev = if (insertionIdx > 0) list[insertionIdx - 1] else null
            val next = if (insertionIdx < list.size) list[insertionIdx] else null
            Pair(prev, next)
        }
    }

    companion object {
        const val DATABASE_NAME = "dictionary.db"

        @Volatile
        private var instance: DictionaryRepository? = null

        fun getInstance(
            dbProvider: () -> DictionaryDatabase
        ): DictionaryRepository {
            return instance ?: synchronized(this) {
                instance ?: DictionaryRepository(dbProvider()).also { instance = it }
            }
        }
    }
}
