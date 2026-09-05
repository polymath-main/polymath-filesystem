package com.polymath.fs.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.polymath.fs.data.db.dao.BookmarkDao
import com.polymath.fs.data.db.dao.OperationHistoryDao
import com.polymath.fs.data.db.dao.RecentFileDao
import com.polymath.fs.data.db.dao.SearchIndexDao
import com.polymath.fs.data.db.entities.BookmarkEntity
import com.polymath.fs.data.db.entities.OperationHistoryEntity
import com.polymath.fs.data.db.entities.RecentFileEntity
import com.polymath.fs.data.db.entities.SearchIndexEntity

@Database(
    entities = [
        RecentFileEntity::class,
        SearchIndexEntity::class,
        BookmarkEntity::class,
        OperationHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun operationHistoryDao(): OperationHistoryDao
}
