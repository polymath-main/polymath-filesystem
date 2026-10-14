package com.polymath.fs

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.polymath.fs.core.DirectoryWatcher
import com.polymath.fs.core.RootShellHolder
import com.polymath.fs.core.VolumeManager
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.repository.IFileSystemRepository
import com.polymath.fs.data.repository.VfsRepository
import com.polymath.fs.data.saf.SafPermissionManager
import com.polymath.fs.domain.usecase.CopyFilesUseCase
import com.polymath.fs.domain.usecase.DeleteFilesUseCase
import com.polymath.fs.domain.usecase.ListDirUseCase
import com.polymath.fs.domain.usecase.MkdirUseCase
import com.polymath.fs.domain.usecase.MoveFilesUseCase
import com.polymath.fs.domain.usecase.RenameUseCase
import com.polymath.fs.domain.vfs.VfsRegistry
import com.polymath.fs.domain.vfs.provider.LocalVfsProvider
import com.polymath.fs.domain.vfs.provider.SafVfsProvider
import com.polymath.fs.js.PolymathJSBridge
import com.topjohnwu.superuser.Shell

class PolymathApp : Application() {

    val shellHolder by lazy { RootShellHolder() }
    val localVfsProvider by lazy { LocalVfsProvider(shellHolder) }
    val safVfsProvider by lazy { SafVfsProvider(this) }
    val vfsRegistry by lazy { VfsRegistry(listOf(localVfsProvider, safVfsProvider)) }
    val safPermissionManager by lazy { SafPermissionManager(this, AppDatabase.getDatabase(this).safGrantDao()) }
    val fileSystemRepository: IFileSystemRepository by lazy { VfsRepository(vfsRegistry, this) }
    val jsBridge by lazy { PolymathJSBridge(fileSystemRepository, this, shellHolder) }
    val jsRuntime by lazy { jsBridge.jsRuntime }
    val listDirUseCase by lazy { ListDirUseCase(fileSystemRepository) }
    val deleteFilesUseCase by lazy { DeleteFilesUseCase(fileSystemRepository) }
    val renameUseCase by lazy { RenameUseCase(fileSystemRepository) }
    val copyFilesUseCase by lazy { CopyFilesUseCase(fileSystemRepository) }
    val moveFilesUseCase by lazy { MoveFilesUseCase(fileSystemRepository) }
    val mkdirUseCase by lazy { MkdirUseCase(fileSystemRepository) }
    val volumeManager by lazy { VolumeManager(this) }
    val directoryWatcher by lazy { DirectoryWatcher() }
    val flowStateManager by lazy { com.polymath.fs.core.FlowStateManager(this) }
    val intentEngine by lazy { com.polymath.fs.core.IntentEngine(this) }
    val synapseEngine by lazy { com.polymath.fs.core.SynapseEngine(this, fileSystemRepository) }
    val smartFolderManager by lazy { com.polymath.fs.core.SmartFolderManager() }

    override fun onCreate() {
        super.onCreate()
        
        // Configure libsu
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "system")
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark", "amoled" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // Initialize and extract built-in script library
        com.polymath.fs.core.BuiltInScriptManager.extractAllBuiltInScripts(this, forceOverwrite = false)
    }
}
