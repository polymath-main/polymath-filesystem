package com.polymath.fs.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

object ThemeManager {

    private val _themeChangeEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val themeChangeEvents: SharedFlow<String> = _themeChangeEvents.asSharedFlow()

    data class ThemeColors(
        val name: String,
        val background: Int,
        val surface: Int,
        val primary: Int,
        val accent: Int,
        val textPrimary: Int,
        val textSecondary: Int
    )

    fun getThemeResId(context: Context): Int {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeKey = prefs.getString("theme", "cyberpunk") ?: "cyberpunk"
        return getThemeResIdByKey(themeKey)
    }

    fun getThemeResIdByKey(themeKey: String): Int {
        return when (themeKey.lowercase()) {
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
    }

    fun applyTheme(activity: AppCompatActivity) {
        val resId = getThemeResId(activity)
        activity.setTheme(resId)
    }

    fun setTheme(context: Context, themeKey: String) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString("theme", themeKey).apply()
        _themeChangeEvents.tryEmit(themeKey)
    }

    fun applyCustomJsonTheme(context: Context, jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("custom_theme_json", jsonStr)

            val primaryBg = json.optString("primaryBg", "").lowercase()
            val textColor = json.optString("textColor", "").lowercase()
            val accentColor = json.optString("accentColor", "").lowercase()

            val detectedTheme = when {
                primaryBg == "#000000" && (accentColor == "#aaaaaa" || textColor == "#ffffff") -> "true_amoled"
                accentColor.contains("ff003c") || primaryBg.contains("fcee0a") || textColor.contains("00ff9f") -> "cyberpunk"
                textColor == "#00ff41" || accentColor == "#008f11" -> "hacker_green"
                primaryBg == "#0f172a" || primaryBg == "#1e293b" -> "dracula"
                primaryBg.contains("f8fafc") || primaryBg.contains("ffffff") -> "ocean_breeze"
                else -> "cyberpunk"
            }

            editor.putString("theme", detectedTheme)
            editor.apply()
            _themeChangeEvents.tryEmit(detectedTheme)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getThemeColors(context: Context): ThemeColors {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeKey = prefs.getString("theme", "cyberpunk") ?: "cyberpunk"
        return when (themeKey.lowercase()) {
            "ocean_breeze" -> ThemeColors(
                name = "Ocean Breeze",
                background = Color.parseColor("#F0F4F8"),
                surface = Color.parseColor("#FFFFFF"),
                primary = Color.parseColor("#0284C7"),
                accent = Color.parseColor("#38BDF8"),
                textPrimary = Color.parseColor("#0F172A"),
                textSecondary = Color.parseColor("#64748B")
            )
            "dracula" -> ThemeColors(
                name = "Dracula",
                background = Color.parseColor("#282A36"),
                surface = Color.parseColor("#44475A"),
                primary = Color.parseColor("#BD93F9"),
                accent = Color.parseColor("#FF79C6"),
                textPrimary = Color.parseColor("#F8F8F2"),
                textSecondary = Color.parseColor("#6272A4")
            )
            "solarized_dark" -> ThemeColors(
                name = "Solarized Dark",
                background = Color.parseColor("#002B36"),
                surface = Color.parseColor("#073642"),
                primary = Color.parseColor("#268BD2"),
                accent = Color.parseColor("#2AA198"),
                textPrimary = Color.parseColor("#839496"),
                textSecondary = Color.parseColor("#586E75")
            )
            "sunset" -> ThemeColors(
                name = "Sunset",
                background = Color.parseColor("#1B1A17"),
                surface = Color.parseColor("#2E2B2A"),
                primary = Color.parseColor("#F26419"),
                accent = Color.parseColor("#F6AE2D"),
                textPrimary = Color.parseColor("#EDF2F4"),
                textSecondary = Color.parseColor("#8D99AE")
            )
            "hacker_green" -> ThemeColors(
                name = "Hacker Green",
                background = Color.parseColor("#0D0D0D"),
                surface = Color.parseColor("#1A1A1A"),
                primary = Color.parseColor("#00FF41"),
                accent = Color.parseColor("#008F11"),
                textPrimary = Color.parseColor("#00FF41"),
                textSecondary = Color.parseColor("#008F11")
            )
            "neon" -> ThemeColors(
                name = "Neon",
                background = Color.parseColor("#120024"),
                surface = Color.parseColor("#220042"),
                primary = Color.parseColor("#FF007F"),
                accent = Color.parseColor("#00F0FF"),
                textPrimary = Color.parseColor("#FFFFFF"),
                textSecondary = Color.parseColor("#B388FF")
            )
            "material_you" -> ThemeColors(
                name = "Material You",
                background = Color.parseColor("#1C1B1F"),
                surface = Color.parseColor("#2B2930"),
                primary = Color.parseColor("#D0BCFF"),
                accent = Color.parseColor("#CCC2DC"),
                textPrimary = Color.parseColor("#E6E1E5"),
                textSecondary = Color.parseColor("#938F99")
            )
            "ios_like" -> ThemeColors(
                name = "iOS Like",
                background = Color.parseColor("#F2F2F7"),
                surface = Color.parseColor("#FFFFFF"),
                primary = Color.parseColor("#007AFF"),
                accent = Color.parseColor("#5856D6"),
                textPrimary = Color.parseColor("#000000"),
                textSecondary = Color.parseColor("#8E8E93")
            )
            "true_amoled" -> ThemeColors(
                name = "True AMOLED",
                background = Color.parseColor("#000000"),
                surface = Color.parseColor("#111111"),
                primary = Color.parseColor("#FFFFFF"),
                accent = Color.parseColor("#AAAAAA"),
                textPrimary = Color.parseColor("#FFFFFF"),
                textSecondary = Color.parseColor("#777777")
            )
            else -> ThemeColors( // Cyberpunk
                name = "Cyberpunk",
                background = Color.parseColor("#0F172A"),
                surface = Color.parseColor("#1E293B"),
                primary = Color.parseColor("#00FFCC"),
                accent = Color.parseColor("#FF0055"),
                textPrimary = Color.parseColor("#FFFFFF"),
                textSecondary = Color.parseColor("#94A3B8")
            )
        }
    }
}
