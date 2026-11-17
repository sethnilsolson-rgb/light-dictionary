package com.thelightphone.dictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object DictionaryService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchWordOnline(word: String): Result<WordEntry> = withContext(Dispatchers.IO) {
        val entry = withTimeoutOrNull(2000L) {
            runCatching {
                var connection: HttpURLConnection? = null
                try {
                    val encoded = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
                    val url = URI("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded").toURL()

                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 1500
                        readTimeout = 1500
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        return@runCatching null
                    }

                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val entries = json.decodeFromString<List<ApiWordEntry>>(body)
                    val first = entries.firstOrNull() ?: return@runCatching null

                    val phoneticText = first.phonetic
                        ?: first.phonetics.firstOrNull { !it.text.isNullOrBlank() }?.text
                        ?: ""

                    val meanings = first.meanings.map { m ->
                        WordMeaning(
                            partOfSpeech = m.partOfSpeech,
                            definitions = m.definitions.map { d ->
                                WordDefinition(
                                    definition = d.definition,
                                    example = d.example,
                                    synonyms = d.synonyms,
                                    antonyms = d.antonyms,
                                )
                            },
                            synonyms = m.synonyms,
                            antonyms = m.antonyms,
                        )
                    }

                    WordEntry(
                        word = first.word.ifBlank { word },
                        phonetic = phoneticText,
                        origin = first.origin ?: "",
                        meanings = meanings,
                    )
                } finally {
                    connection?.disconnect()
                }
            }.getOrNull()
        }

        if (entry != null) {
            Result.success(entry)
        } else {
            Result.failure(NoSuchElementException("Word not found online"))
        }
    }
}
