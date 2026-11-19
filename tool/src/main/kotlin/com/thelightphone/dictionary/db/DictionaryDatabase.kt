package com.thelightphone.dictionary.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflineWordEntity::class,
        ScrabbleWordEntity::class,
        EtymologyEntity::class,
        CachedWordEntity::class,
        BookmarkWordEntity::class,
        RecentSearchEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}
