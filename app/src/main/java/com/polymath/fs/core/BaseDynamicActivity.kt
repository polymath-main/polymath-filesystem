package com.polymath.fs.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

abstract class BaseDynamicActivity : AppCompatActivity() {
    
    private var currentThemeResId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        currentThemeResId = ThemeManager.getThemeResId(this)
        setTheme(currentThemeResId)
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                ThemeManager.themeChangeEvents.collect {
                    val newResId = ThemeManager.getThemeResId(this@BaseDynamicActivity)
                    if (newResId != currentThemeResId) {
                        recreate()
                    }
                }
            }
        }
    }
}
