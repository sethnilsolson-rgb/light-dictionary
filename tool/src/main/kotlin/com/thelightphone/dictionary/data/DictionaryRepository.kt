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

        // 4. Check if word is a recognized English word in Scrabble tournament lexicon
        val isScrabble = database.dictionaryDao().isScrabbleLegal(normalized)
        if (!isScrabble) {
            // Not in Webster (102k words) and not in Scrabble (83k words) -> typo or non-word!
            // Skip network entirely to avoid blocking the user on "Looking up..."
            return@withContext null
        }

        // 5. Valid English word: attempt online fetch for definitions
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

        // 6. Offline fallback for Scrabble word when network is unavailable
        recordRecentSearch(normalized)
        WordEntry(
            word = normalized,
            phonetic = "",
            origin = "Tournament word list",
            meanings = listOf(
                WordMeaning(
                    partOfSpeech = "word",
                    definitions = listOf(
                        WordDefinition(
                            definition = "Valid English word (Scrabble tournament verified). Connect to network to fetch full definitions and examples."
                        )
                    )
                )
            )
        )

        null
    }

    suspend fun getSuggestions(word: String): List<String> = withContext(Dispatchers.IO) {
        val q = word.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()

        // 1. Handle repeated character collapse (e.g. crilll -> crill and cril)
        val collapsed = q.replace(Regex("(.)\\1+"), "$1$1")
        val single = q.replace(Regex("(.)\\1+"), "$1")

        val minLen = minOf(q.length, collapsed.length, single.length).minus(2).coerceAtLeast(2)
        val maxLen = maxOf(q.length, collapsed.length) + 2

        val prefixes = mutableSetOf<String>()
        for (base in listOf(q, collapsed, single)) {
            if (base.length >= 4) prefixes.add(base.substring(0, 4))
            if (base.length >= 3) prefixes.add(base.substring(0, 3))
            prefixes.add(base.substring(0, 2))
        }

        // 2. Phonetic variations on prefixes
        val phoneticPrefixes = mutableSetOf<String>()
        for (p in prefixes) {
            when {
                p.startsWith("c") -> {
                    phoneticPrefixes.add("k" + p.substring(1))
                    phoneticPrefixes.add("s" + p.substring(1))
                }
                p.startsWith("k") -> phoneticPrefixes.add("c" + p.substring(1))
                p.startsWith("ph") -> phoneticPrefixes.add("f" + p.substring(2))
                p.startsWith("f") -> phoneticPrefixes.add("ph" + p.substring(1))
                p.startsWith("w") -> phoneticPrefixes.add("wh" + p.substring(1))
                p.startsWith("wh") -> phoneticPrefixes.add("w" + p.substring(2))
                p.startsWith("sc") -> phoneticPrefixes.add("sk" + p.substring(2))
                p.startsWith("sk") -> phoneticPrefixes.add("sc" + p.substring(2))
                p.startsWith("j") -> phoneticPrefixes.add("g" + p.substring(1))
                p.startsWith("g") -> phoneticPrefixes.add("j" + p.substring(1))
                p.startsWith("x") -> {
                    phoneticPrefixes.add("z" + p.substring(1))
                    phoneticPrefixes.add("xy")
                    phoneticPrefixes.add("ex" + p.substring(1))
                }
            }
        }
        prefixes.addAll(phoneticPrefixes)

        val candidates = mutableSetOf<String>()

        // 3. Query candidate prefixes across offline_words and scrabble_words
        for (p in prefixes) {
            candidates.addAll(database.dictionaryDao().getCandidateWords("$p%", minLen, maxLen))
            candidates.addAll(database.dictionaryDao().getScrabbleCandidateWords("$p%", minLen, maxLen))
        }

        // 4. Query candidate suffixes across offline_words and scrabble_words (e.g. "...ill")
        for (base in listOf(collapsed, q)) {
            if (base.length >= 4) {
                val suf = base.takeLast(3)
                candidates.addAll(database.dictionaryDao().getCandidateWordsBySuffix("%$suf", minLen, maxLen))
                candidates.addAll(database.dictionaryDao().getScrabbleWordsBySuffix("%$suf", minLen, maxLen))
            }
        }

        val builtInWords = BuiltInDictionary.entries.map { it.word }
        candidates.addAll(builtInWords)

        // 5. Score candidates with Levenshtein distance against q, collapsed, and single
        val scored = candidates
            .map { w ->
                val dist = minOf(
                    WebsterParser.levenshteinDistance(q, w),
                    WebsterParser.levenshteinDistance(collapsed, w),
                    WebsterParser.levenshteinDistance(single, w)
                )
                Pair(w, dist)
            }
            .filter { it.second <= 3 }
            .sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { kotlin.math.abs(it.first.length - collapsed.length) }
                    .thenBy { it.first }
            )
            .map { it.first }

        if (scored.isNotEmpty()) {
            return@withContext scored.take(8)
        }

        // Fallback for extreme typos (distance <= 4)
        val fallbackScored = candidates
            .map { w ->
                val dist = minOf(
                    WebsterParser.levenshteinDistance(q, w),
                    WebsterParser.levenshteinDistance(collapsed, w),
                    WebsterParser.levenshteinDistance(single, w)
                )
                Pair(w, dist)
            }
            .filter { it.second <= 4 }
            .sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { kotlin.math.abs(it.first.length - collapsed.length) }
                    .thenBy { it.first }
            )
            .map { it.first }

        fallbackScored.take(8)
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
            candidates.addAll(database.dictionaryDao().getScrabbleCandidateWords("$p%", minLen, maxLen))
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
