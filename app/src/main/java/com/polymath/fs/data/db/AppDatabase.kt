package com.polymath.fs.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.polymath.fs.data.db.dao.BookmarkDao
import com.polymath.fs.data.db.dao.CanvasPresetDao
import com.polymath.fs.data.db.dao.CognitiveCanvasDao
import com.polymath.fs.data.db.dao.OperationHistoryDao
import com.polymath.fs.data.db.dao.RecentFileDao
import com.polymath.fs.data.db.dao.SearchIndexDao
import com.polymath.fs.data.db.dao.WorkspaceSnapshotDao
import com.polymath.fs.data.db.entities.BookmarkEntity
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import com.polymath.fs.data.db.entities.OperationHistoryEntity
import com.polymath.fs.data.db.entities.RecentFileEntity
import com.polymath.fs.data.db.entities.SearchIndexEntity
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity

@Database(
    entities = [
        RecentFileEntity::class,
        SearchIndexEntity::class,
        BookmarkEntity::class,
        OperationHistoryEntity::class,
        CanvasNodeEntity::class,
        CanvasEdgeEntity::class,
        WorkspaceSnapshotEntity::class,
        CanvasPresetEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun operationHistoryDao(): OperationHistoryDao
    abstract fun cognitiveCanvasDao(): CognitiveCanvasDao
    abstract fun workspaceSnapshotDao(): WorkspaceSnapshotDao
    abstract fun canvasPresetDao(): CanvasPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "polymath_filesystem.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
