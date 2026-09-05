package com.polymath.fs

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PolymathApp : Application() {
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
    }
}
