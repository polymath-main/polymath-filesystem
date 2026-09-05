package com.polymath.fs.ui

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.polymath.fs.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val switchIconPack = findViewById<Switch>(R.id.switchIconPack)

        val currentTheme = prefs.getString("theme", "system")
        when (currentTheme) {
            "light" -> rgTheme.check(R.id.rbLight)
            "dark" -> rgTheme.check(R.id.rbDark)
            "amoled" -> rgTheme.check(R.id.rbAmoled)
            else -> rgTheme.check(R.id.rbSystem)
        }

        switchIconPack.isChecked = prefs.getBoolean("custom_icons", false)

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val themeStr = when (checkedId) {
                R.id.rbLight -> "light"
                R.id.rbDark -> "dark"
                R.id.rbAmoled -> "amoled"
                else -> "system"
            }
            prefs.edit().putString("theme", themeStr).apply()
            applyTheme(themeStr)
        }

        switchIconPack.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("custom_icons", isChecked).apply()
        }
    }

    private fun applyTheme(theme: String?) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark", "amoled" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
