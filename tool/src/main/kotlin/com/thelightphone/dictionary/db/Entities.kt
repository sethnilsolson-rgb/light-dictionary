package com.thelightphone.dictionary.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_words")
data class OfflineWordEntity(
    @PrimaryKey
    val word: String,
    val definition: String,
)

@Entity(tableName = "cached_words")
data class CachedWordEntity(
    @PrimaryKey
    val word: String,
    val phonetic: String = "",
    val origin: String = "",
    val meaningsJson: String = "", // serialized JSON of meanings
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bookmarked_words")
data class BookmarkWordEntity(
    @PrimaryKey
    val word: String,
    val snippet: String = "",
    val partOfSpeech: String = "",
    val bookmarkedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey
    val word: String,
    val searchedAt: Long = System.currentTimeMillis(),
)
