package com.polymath.fs.ui

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polymath.fs.core.BaseDynamicActivity

import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.polymath.fs.R
import com.polymath.fs.core.BuiltInScriptManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseDynamicActivity() {

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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val switchRootExplorer = findViewById<SwitchMaterial>(R.id.switchRootExplorer)
        val tvRootStatus = findViewById<TextView>(R.id.tvRootStatus)
        val chipGroupThemes = findViewById<ChipGroup>(R.id.chipGroupThemes)
        val chipGroupIcons = findViewById<ChipGroup>(R.id.chipGroupIcons)
        val switchHiddenFiles = findViewById<SwitchMaterial>(R.id.switchHiddenFiles)
        val switchConfirmDelete = findViewById<SwitchMaterial>(R.id.switchConfirmDelete)
        val switchBgScheduling = findViewById<SwitchMaterial>(R.id.switchBgScheduling)
        val btnResetExtensions = findViewById<MaterialButton>(R.id.btnResetExtensions)

        // 1. Root Explorer
        val isRootEnabled = prefs.getBoolean("root_explorer", false)
        switchRootExplorer.isChecked = isRootEnabled
        updateRootStatusText(tvRootStatus, isRootEnabled)

        switchRootExplorer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvRootStatus.text = "Status: Verifying root access with libsu..."
                lifecycleScope.launch(Dispatchers.IO) {
                    val rootAvailable = try {
                        val shell = Shell.getShell()
                        shell.isRoot
                    } catch (e: Exception) {
                        false
                    }

                    withContext(Dispatchers.Main) {
                        prefs.edit().putBoolean("root_explorer", true).apply()
                        updateRootStatusText(tvRootStatus, true)
                        Toast.makeText(this@SettingsActivity, "Root Explorer mode enabled", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                prefs.edit().putBoolean("root_explorer", false).apply()
                updateRootStatusText(tvRootStatus, false)
                Toast.makeText(this@SettingsActivity, "Root Explorer disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Inline Themes
        val activeThemeChipId = when (currentTheme) {
            "cyberpunk" -> R.id.chipCyberpunk
            "ocean_breeze" -> R.id.chipOceanBreeze
            "dracula" -> R.id.chipDracula
            "solarized_dark" -> R.id.chipSolarizedDark
            "sunset" -> R.id.chipSunset
            "hacker_green" -> R.id.chipHackerGreen
            "neon" -> R.id.chipNeon
            "material_you" -> R.id.chipMaterialYou
            "ios_like" -> R.id.chipIosLike
            "true_amoled" -> R.id.chipTrueAmoled
            else -> R.id.chipCyberpunk
        }
        chipGroupThemes.check(activeThemeChipId)

        chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedId = checkedIds.first()
            val themeStr = when (selectedId) {
                R.id.chipCyberpunk -> "cyberpunk"
                R.id.chipOceanBreeze -> "ocean_breeze"
                R.id.chipDracula -> "dracula"
                R.id.chipSolarizedDark -> "solarized_dark"
                R.id.chipSunset -> "sunset"
                R.id.chipHackerGreen -> "hacker_green"
                R.id.chipNeon -> "neon"
                R.id.chipMaterialYou -> "material_you"
                R.id.chipIosLike -> "ios_like"
                R.id.chipTrueAmoled -> "true_amoled"
                else -> "cyberpunk"
            }
            if (themeStr != currentTheme) {
                prefs.edit().putString("theme", themeStr).apply()
                recreate()
            }
        }

        // 3. Inline Icons
        val currentIconPack = prefs.getString("icon_pack", "default")
        val activeIconChipId = when (currentIconPack) {
            "macos" -> R.id.chipIconMacos
            "outline" -> R.id.chipIconOutline
            "solid" -> R.id.chipIconSolid
            "minimal" -> R.id.chipIconMinimal
            else -> R.id.chipIconDefault
        }
        chipGroupIcons.check(activeIconChipId)

        chipGroupIcons.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedId = checkedIds.first()
            val packStr = when (selectedId) {
                R.id.chipIconMacos -> "macos"
                R.id.chipIconOutline -> "outline"
                R.id.chipIconSolid -> "solid"
                R.id.chipIconMinimal -> "minimal"
                else -> "default"
            }
            prefs.edit().putString("icon_pack", packStr).apply()
            Toast.makeText(this, "Icon pack updated to: $packStr", Toast.LENGTH_SHORT).show()
        }

        // 4. File system behavior
        switchHiddenFiles.isChecked = prefs.getBoolean("show_hidden_files", false)
        switchHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_hidden_files", isChecked).apply()
        }

        switchConfirmDelete.isChecked = prefs.getBoolean("confirm_delete", true)
        switchConfirmDelete.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("confirm_delete", isChecked).apply()
        }

        // 5. Background Scheduling
        switchBgScheduling.isChecked = prefs.getBoolean("js_background_work", true)
        switchBgScheduling.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("js_background_work", isChecked).apply()
        }

        // 6. Reset Extensions
        btnResetExtensions.setOnClickListener {
            BuiltInScriptManager.extractAllBuiltInScripts(this, forceOverwrite = true)
            Toast.makeText(this, "All built-in extensions re-synchronized successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRootStatusText(tvStatus: TextView, isEnabled: Boolean) {
        if (!isEnabled) {
            tvStatus.text = "Status: Root disabled (Standard non-root mode)"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        } else {
            val isRooted = try {
                Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
            } catch (e: Exception) {
                false
            }
            if (isRooted) {
                tvStatus.text = "Status: Root active (SU granted via libsu)"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#22C55E"))
            } else {
                tvStatus.text = "Status: Root mode active (System elevated access requested)"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#EAB308"))
            }
        }
    }
}
