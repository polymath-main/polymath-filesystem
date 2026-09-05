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
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("theme", "cyberpunk")
        val themeResId = when (currentTheme) {
            "cyberpunk" -> R.style.Theme_Cyberpunk
            "ocean_breeze" -> R.style.Theme_OceanBreeze
            "dracula" -> R.style.Theme_Dracula
            "solarized_dark" -> R.style.Theme_SolarizedDark
            "sunset" -> R.style.Theme_Sunset
            "hacker_green" -> R.style.Theme_HackerGreen
            "neon" -> R.style.Theme_Neon
            "material_you" -> R.style.Theme_MaterialYou
            "ios_like" -> R.style.Theme_IosLike
            "true_amoled" -> R.style.Theme_TrueAmoled
            else -> R.style.Theme_Cyberpunk
        }
        setTheme(themeResId)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val rgPrimaryColor = findViewById<RadioGroup>(R.id.rgPrimaryColor)
        val rgIconPack = findViewById<RadioGroup>(R.id.rgIconPack)

        when (currentTheme) {
            "cyberpunk" -> rgTheme.check(R.id.rbCyberpunk)
            "ocean_breeze" -> rgTheme.check(R.id.rbOceanBreeze)
            "dracula" -> rgTheme.check(R.id.rbDracula)
            "solarized_dark" -> rgTheme.check(R.id.rbSolarizedDark)
            "sunset" -> rgTheme.check(R.id.rbSunset)
            "hacker_green" -> rgTheme.check(R.id.rbHackerGreen)
            "neon" -> rgTheme.check(R.id.rbNeon)
            "material_you" -> rgTheme.check(R.id.rbMaterialYou)
            "ios_like" -> rgTheme.check(R.id.rbIosLike)
            "true_amoled" -> rgTheme.check(R.id.rbTrueAmoled)
            else -> rgTheme.check(R.id.rbCyberpunk)
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
                R.id.rbCyberpunk -> "cyberpunk"
                R.id.rbOceanBreeze -> "ocean_breeze"
                R.id.rbDracula -> "dracula"
                R.id.rbSolarizedDark -> "solarized_dark"
                R.id.rbSunset -> "sunset"
                R.id.rbHackerGreen -> "hacker_green"
                R.id.rbNeon -> "neon"
                R.id.rbMaterialYou -> "material_you"
                R.id.rbIosLike -> "ios_like"
                R.id.rbTrueAmoled -> "true_amoled"
                else -> "cyberpunk"
            }
            prefs.edit().putString("theme", themeStr).apply()
            recreate()
        }

        rgPrimaryColor.setOnCheckedChangeListener { _, checkedId ->
            val colorStr = when (checkedId) {
                R.id.rbColorGreen -> "green"
                R.id.rbColorPurple -> "purple"
                else -> "blue"
            }
            prefs.edit().putString("primary_color", colorStr).apply()
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
}
