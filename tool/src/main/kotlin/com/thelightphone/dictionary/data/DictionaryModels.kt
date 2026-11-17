package com.thelightphone.dictionary.data

import kotlinx.serialization.Serializable

@Serializable
data class WordEntry(
    val word: String,
    val phonetic: String = "",
    val origin: String = "",
    val meanings: List<WordMeaning> = emptyList(),
)

@Serializable
data class WordMeaning(
    val partOfSpeech: String = "",
    val definitions: List<WordDefinition> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
)

@Serializable
data class WordDefinition(
    val definition: String = "",
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
)

// DTO for Free Dictionary API: https://api.dictionaryapi.dev/api/v2/entries/en/<word>
@Serializable
data class ApiWordEntry(
    val word: String = "",
    val phonetic: String? = null,
    val phonetics: List<ApiPhonetic> = emptyList(),
    val origin: String? = null,
    val meanings: List<ApiMeaning> = emptyList(),
)

@Serializable
data class ApiPhonetic(
    val text: String? = null,
    val audio: String? = null,
)

@Serializable
data class ApiMeaning(
    val partOfSpeech: String = "",
    val definitions: List<ApiDefinition> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
)

@Serializable
data class ApiDefinition(
    val definition: String = "",
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
)
