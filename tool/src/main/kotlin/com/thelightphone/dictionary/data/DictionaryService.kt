package com.thelightphone.dictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        runCatching {
            val encoded = URLEncoder.encode(word.trim().lowercase(), "UTF-8")
            val url = URI("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded").toURL()

            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LightPhone-Dictionary/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                error("Word not found (HTTP $responseCode)")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val entries = json.decodeFromString<List<ApiWordEntry>>(body)
            val first = entries.firstOrNull() ?: error("Empty dictionary response")

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
        }
    }
}
