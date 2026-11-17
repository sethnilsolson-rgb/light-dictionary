package com.thelightphone.dictionary.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedWordEntity::class,
        BookmarkWordEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
