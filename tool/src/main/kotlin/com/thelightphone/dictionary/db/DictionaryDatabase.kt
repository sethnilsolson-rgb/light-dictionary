package com.thelightphone.dictionary.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflineWordEntity::class,
        ScrabbleWordEntity::class,
        CachedWordEntity::class,
        BookmarkWordEntity::class,
        RecentSearchEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
