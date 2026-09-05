package com.polymath.fs.ui

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.polymath.fs.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val rgPrimaryColor = findViewById<RadioGroup>(R.id.rgPrimaryColor)
        val rgIconPack = findViewById<RadioGroup>(R.id.rgIconPack)

        val currentTheme = prefs.getString("theme", "system")
        when (currentTheme) {
            "light" -> rgTheme.check(R.id.rbLight)
            "dark" -> rgTheme.check(R.id.rbDark)
            "amoled" -> rgTheme.check(R.id.rbAmoled)
            else -> rgTheme.check(R.id.rbSystem)
        }

        val primaryColor = prefs.getString("primary_color", "blue")
        when (primaryColor) {
            "green" -> rgPrimaryColor.check(R.id.rbColorGreen)
            "purple" -> rgPrimaryColor.check(R.id.rbColorPurple)
            else -> rgPrimaryColor.check(R.id.rbColorBlue)
        }

        val iconPack = prefs.getString("icon_pack", "default")
        when (iconPack) {
            "outline" -> rgIconPack.check(R.id.rbIconOutline)
            "minimal" -> rgIconPack.check(R.id.rbIconMinimal)
            else -> rgIconPack.check(R.id.rbIconDefault)
        }

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

        rgPrimaryColor.setOnCheckedChangeListener { _, checkedId ->
            val colorStr = when (checkedId) {
                R.id.rbColorGreen -> "green"
                R.id.rbColorPurple -> "purple"
                else -> "blue"
            }
            prefs.edit().putString("primary_color", colorStr).apply()
            // In a real app, we would recreate() to apply theme changes
            recreate()
        }

        rgIconPack.setOnCheckedChangeListener { _, checkedId ->
            val packStr = when (checkedId) {
                R.id.rbIconOutline -> "outline"
                R.id.rbIconMinimal -> "minimal"
                else -> "default"
            }
            prefs.edit().putString("icon_pack", packStr).apply()
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
