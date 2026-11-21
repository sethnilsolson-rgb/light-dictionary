package com.thelightphone.dictionary.data

import com.thelightphone.dictionary.db.BookmarkWordEntity
import com.thelightphone.dictionary.db.CachedWordEntity
import com.thelightphone.dictionary.db.DictionaryDatabase
import com.thelightphone.dictionary.db.RecentSearchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    suspend fun deleteRecentSearch(word: String) {
        if (word.isBlank()) return
        database.dictionaryDao().deleteRecentSearch(word.trim().lowercase())
    }

    suspend fun clearRecentSearches() {
        database.dictionaryDao().clearRecentSearches()
    }

    /**
     * Emits an instant offline definition (0ms) when available, then optionally enriches with
     * online phonetics/examples/modern meanings. Strictly runs on [Dispatchers.IO] and is
     * guaranteed to always emit and terminate cleanly without hanging.
     */
    fun getWordFlow(word: String): Flow<WordEntry?> = flow {
        val normalized = word.trim().lowercase()
        if (normalized.isBlank()) {
            emit(null)
            return@flow
        }

        val dao = database.dictionaryDao()
        val offlineEtymology = runCatching { dao.getEtymology(normalized) }.getOrNull().orEmpty()

        // 1. Check curated built-in dictionary
        val builtIn = BuiltInDictionary.find(normalized)
        if (builtIn != null) {
            recordRecentSearch(normalized)
            val origin = builtIn.origin.ifBlank { offlineEtymology }
            emit(builtIn.copy(origin = origin))
            return@flow
        }

        // 2. Check local database cache (from previous online lookups)
        val cached = runCatching { dao.getCachedWord(normalized) }.getOrNull()
        if (cached != null) {
            val meanings = runCatching {
                json.decodeFromString<List<WordMeaning>>(cached.meaningsJson)
            }.getOrDefault(emptyList())

            if (meanings.isNotEmpty()) {
                recordRecentSearch(normalized)
                val origin = cached.origin.ifBlank { offlineEtymology }
                emit(
                    WordEntry(
                        word = cached.word,
                        phonetic = cached.phonetic,
                        origin = origin,
                        meanings = meanings,
                    )
                )
                return@flow
            }
        }

        // 3. Check 110k+ offline dictionary (0ms latency)
        val offline = runCatching { dao.getOfflineWord(normalized) }.getOrNull()
        val isScrabble = if (offline == null) {
            isScrabbleLegalSync(normalized)
        } else {
            false
        }

        if (offline != null) {
            recordRecentSearch(normalized)
            val parsed = WebsterParser.parse(offline.word, offline.definition)
            emit(parsed.copy(origin = offlineEtymology))
        } else if (isScrabble) {
            // 4. Valid 2-15 letter TWL06 Scrabble word without a Webster entry: emit immediately
            recordRecentSearch(normalized)
            emit(
                WordEntry(
                    word = normalized,
                    phonetic = "",
                    origin = offlineEtymology,
                    meanings = listOf(
                        WordMeaning(
                            partOfSpeech = "word",
                            definitions = listOf(
                                WordDefinition(
                                    definition = "Valid English word (official Scrabble tournament lexicon). Connect to the internet for full modern definitions and examples."
                                )
                            )
                        )
                    )
                )
            )
        } else {
            // Not in offline_words or 178k scrabble_words.
            // Try a short bounded online lookup (2.5s max) for modern words (e.g. "podcast", "emoji"),
            // while guaranteeing typos like "crilll" fail fast and never hang.
            val onlineModern = withTimeoutOrNull(2500L) {
                DictionaryService.fetchWordOnline(normalized).getOrNull()
            }
            if (onlineModern != null && onlineModern.meanings.isNotEmpty()) {
                val finalEntry = onlineModern.copy(origin = onlineModern.origin.ifBlank { offlineEtymology })
                recordRecentSearch(normalized)
                runCatching {
                    dao.insertCachedWord(
                        CachedWordEntity(
                            word = finalEntry.word.lowercase(),
                            phonetic = finalEntry.phonetic,
                            origin = finalEntry.origin,
                            meaningsJson = json.encodeToString(finalEntry.meanings),
                        )
                    )
                }
                emit(finalEntry)
            } else {
                emit(null)
            }
            return@flow
        }

        // 5. Background enrichment for offline/Scrabble words (never blocks initial render)
        val fetched = runCatching {
            DictionaryService.fetchWordOnline(normalized).getOrNull()
        }.getOrNull()

        if (fetched != null && fetched.meanings.isNotEmpty()) {
            val finalOrigin = fetched.origin.ifBlank { offlineEtymology }
            val finalFetched = fetched.copy(origin = finalOrigin)
            runCatching {
                dao.insertCachedWord(
                    CachedWordEntity(
                        word = finalFetched.word.lowercase(),
                        phonetic = finalFetched.phonetic,
                        origin = finalFetched.origin,
                        meaningsJson = json.encodeToString(finalFetched.meanings),
                    )
                )
            }
            emit(finalFetched)
        }
    }.flowOn(Dispatchers.IO)

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
            if (base.length >= 2) prefixes.add(base.substring(0, 2))
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
        val dao = database.dictionaryDao()

        // 3. Query candidate prefixes across offline_words and scrabble_words
        for (p in prefixes) {
            candidates.addAll(dao.getCandidateWords("$p%", minLen, maxLen))
            candidates.addAll(dao.getScrabbleCandidateWords("$p%", minLen, maxLen))
        }

        // 4. Query candidate suffixes across offline_words and scrabble_words (e.g. "...ill")
        for (base in listOf(collapsed, single, q)) {
            if (base.length >= 4) {
                val suf = base.takeLast(3)
                candidates.addAll(dao.getCandidateWordsBySuffix("%$suf", minLen, maxLen))
                candidates.addAll(dao.getScrabbleWordsBySuffix("%$suf", minLen, maxLen))
            }
        }

        candidates.addAll(BuiltInDictionary.entries.map { it.word })
        candidates.remove(q)

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

        // Fallback for extreme typos (distance <= 4, plus same-first-letter candidates)
        if (q.first().isLetter()) {
            candidates.addAll(dao.getCandidateWords("${q.first()}%", minLen, maxLen))
        }
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

    private suspend fun isScrabbleLegalSync(normalized: String): Boolean {
        if (normalized.length !in 2..15 || normalized.any { it !in 'a'..'z' }) return false
        return runCatching { database.dictionaryDao().isScrabbleLegal(normalized) }.getOrDefault(false)
    }

    suspend fun isScrabbleLegal(word: String): Boolean = withContext(Dispatchers.IO) {
        isScrabbleLegalSync(word.trim().lowercase())
    }

    suspend fun getSimilarWords(word: String, limit: Int = 6): List<String> = withContext(Dispatchers.IO) {
        val q = word.trim().lowercase()
        if (q.length < 2) return@withContext emptyList()

        val dao = database.dictionaryDao()
        val candidates = mutableSetOf<String>()
        candidates.addAll(dao.getWordsStartingWith("$q%", 50))

        val minLen = (q.length - 2).coerceAtLeast(2)
        val maxLen = q.length + 4
        val prefixes = mutableListOf<String>()
        if (q.length >= 4) prefixes.add(q.substring(0, 4))
        if (q.length >= 3) prefixes.add(q.substring(0, 3))
        prefixes.add(q.substring(0, 2))

        for (p in prefixes) {
            candidates.addAll(dao.getCandidateWords("$p%", minLen, maxLen))
            candidates.addAll(dao.getScrabbleCandidateWords("$p%", minLen, maxLen))
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

    /**
     * Fast inline lookup for press-and-hold word definitions inside a definition, example,
     * or origin text. Checks Built-in, Cached, and 110k+ Offline entries with English
     * stemming first (0ms), then falls back to a bounded online lookup if needed, without
     * polluting recent searches.
     */
    suspend fun quickLookupWord(rawWord: String): WordEntry? = withContext(Dispatchers.IO) {
        val cleaned = rawWord.trim().trim('\'', '’', '-', '.', ',', ';', ':').lowercase()
        if (cleaned.isBlank()) return@withContext null

        val dao = database.dictionaryDao()
        val candidates = buildEnglishCandidates(cleaned)

        // 1. Instant offline pass across Built-in -> Cached -> 110k+ Offline DB
        for (candidate in candidates) {
            BuiltInDictionary.find(candidate)?.let { builtIn ->
                val etym = builtIn.origin.ifBlank { runCatching { dao.getEtymology(candidate) }.getOrNull().orEmpty() }
                return@withContext builtIn.copy(origin = etym)
            }

            val cached = runCatching { dao.getCachedWord(candidate) }.getOrNull()
            if (cached != null) {
                val meanings = runCatching {
                    json.decodeFromString<List<WordMeaning>>(cached.meaningsJson)
                }.getOrDefault(emptyList())
                if (meanings.isNotEmpty()) {
                    val etym = cached.origin.ifBlank { runCatching { dao.getEtymology(candidate) }.getOrNull().orEmpty() }
                    return@withContext WordEntry(
                        word = cached.word,
                        phonetic = cached.phonetic,
                        origin = etym,
                        meanings = meanings,
                    )
                }
            }

            val offline = runCatching { dao.getOfflineWord(candidate) }.getOrNull()
            if (offline != null) {
                val parsed = WebsterParser.parse(offline.word, offline.definition)
                if (parsed.meanings.isNotEmpty()) {
                    val etym = runCatching { dao.getEtymology(candidate) }.getOrNull().orEmpty()
                    return@withContext parsed.copy(origin = etym)
                }
            }
        }

        // 2. Bounded online fallback for modern words not in the offline lexicon
        for (candidate in candidates.take(2)) {
            val online = withTimeoutOrNull(2500L) {
                DictionaryService.fetchWordOnline(candidate).getOrNull()
            }
            if (online != null && online.meanings.isNotEmpty()) {
                val etym = online.origin.ifBlank { runCatching { dao.getEtymology(candidate) }.getOrNull().orEmpty() }
                val finalEntry = online.copy(origin = etym)
                runCatching {
                    dao.insertCachedWord(
                        CachedWordEntity(
                            word = finalEntry.word.lowercase(),
                            phonetic = finalEntry.phonetic,
                            origin = finalEntry.origin,
                            meaningsJson = json.encodeToString(finalEntry.meanings),
                        )
                    )
                }
                return@withContext finalEntry
            }
        }

        null
    }

    /**
     * Finds the true alphabetical previous and next words across the full 110,000+ offline dictionary.
     */
    suspend fun getAdjacentWords(currentWord: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val normalized = currentWord.trim().lowercase()
        if (normalized.isBlank()) return@withContext Pair(null, null)
        val dao = database.dictionaryDao()
        val prev = runCatching { dao.getPrevOfflineWord(normalized) }.getOrNull()
        val next = runCatching { dao.getNextOfflineWord(normalized) }.getOrNull()
        if (prev != null || next != null) {
            return@withContext Pair(prev, next)
        }
        // Fallback to built-in index if needed
        val list = getAllKnownWordsAlphabetical()
        val idx = list.indexOf(normalized)
        if (idx != -1) {
            Pair(list.getOrNull(idx - 1), list.getOrNull(idx + 1))
        } else {
            Pair(null, null)
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

        fun extractWordAt(text: String, charIndex: Int): String? {
            if (text.isEmpty()) return null
            var idx = charIndex.coerceIn(0, text.lastIndex)
            if (!isWordChar(text[idx])) {
                if (idx > 0 && isWordChar(text[idx - 1])) idx--
                else if (idx + 1 < text.length && isWordChar(text[idx + 1])) idx++
                else return null
            }
            var start = idx
            while (start > 0 && isWordChar(text[start - 1])) start--
            var end = idx
            while (end + 1 < text.length && isWordChar(text[end + 1])) end++
            return text.substring(start, end + 1).trim('\'', '-', '’').takeIf { it.isNotBlank() }
        }

        fun buildEnglishCandidates(normalized: String): List<String> {
            val list = LinkedHashSet<String>()
            list.add(normalized)
            if (normalized.endsWith("ies") && normalized.length > 4) list.add(normalized.dropLast(3) + "y")
            if (normalized.endsWith("ied") && normalized.length > 4) list.add(normalized.dropLast(3) + "y")
            if (normalized.endsWith("ing") && normalized.length > 5) {
                val stem = normalized.dropLast(3)
                list.add(stem)
                list.add(stem + "e")
                if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) list.add(stem.dropLast(1))
            }
            if (normalized.endsWith("ed") && normalized.length > 4) {
                val stem = normalized.dropLast(2)
                list.add(stem)
                list.add(stem + "e")
                if (stem.length >= 2 && stem.last() == stem[stem.lastIndex - 1]) list.add(stem.dropLast(1))
            }
            if (normalized.endsWith("ly") && normalized.length > 4) list.add(normalized.dropLast(2))
            if (normalized.endsWith("est") && normalized.length > 4) {
                list.add(normalized.dropLast(3))
                list.add(normalized.dropLast(3) + "e")
            }
            if (normalized.endsWith("er") && normalized.length > 4) {
                list.add(normalized.dropLast(2))
                list.add(normalized.dropLast(2) + "e")
            }
            if (normalized.endsWith("es") && normalized.length > 3) list.add(normalized.dropLast(2))
            if (normalized.endsWith("s") && normalized.length > 3 && !normalized.endsWith("ss")) {
                list.add(normalized.dropLast(1))
            }
            return list.toList()
        }

        private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’' || c == '-'
    }
}
