package com.polymath.fs.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BuiltInScriptManager
import com.polymath.fs.databinding.ActivityScriptManagerBinding
import com.polymath.fs.viewers.EditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScriptManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScriptManagerBinding
    private val allScriptFiles = mutableListOf<File>()
    private val displayedScriptFiles = mutableListOf<File>()
    private lateinit var scriptAdapter: ScriptAdapter
    private lateinit var scriptsDir: File
    private var selectedCategory: String = "All"

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
        binding = ActivityScriptManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        scriptsDir = BuiltInScriptManager.getScriptsDirectory(this)

        setupRecyclerView()
        setupCategoryChips()
        binding.fabNewScript.setOnClickListener { showCreateScriptDialog() }

        // Extract built-in scripts collection if needed
        BuiltInScriptManager.extractAllBuiltInScripts(this, forceOverwrite = false)
        loadScripts()
    }

    override fun onResume() {
        super.onResume()
        loadScripts()
    }

    private fun setupCategoryChips() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when {
                checkedIds.contains(R.id.chip_pinned) -> "Pinned"
                checkedIds.contains(R.id.chip_organizer) -> "Organizer"
                checkedIds.contains(R.id.chip_security) -> "Security"
                checkedIds.contains(R.id.chip_themes) -> "Themes"
                checkedIds.contains(R.id.chip_network) -> "Network"
                checkedIds.contains(R.id.chip_utils) -> "Utils"
                checkedIds.contains(R.id.chip_core) -> "Core"
                checkedIds.contains(R.id.chip_analytics) -> "SystemAnalytics"
                checkedIds.contains(R.id.chip_os_runtime) -> "OSRuntime"
                else -> "All"
            }
            filterScripts()
        }
    }

    private fun setupRecyclerView() {
        scriptAdapter = ScriptAdapter(displayedScriptFiles, object : ScriptAdapter.OnScriptClickListener {
            override fun onRun(script: File) {
                runScript(script)
            }

            override fun onEdit(script: File) {
                editScript(script)
            }

            override fun onPin(script: File) {
                val pinsPref = getSharedPreferences("script_pins", Context.MODE_PRIVATE)
                val isPinned = pinsPref.getBoolean(script.absolutePath, false)
                pinsPref.edit().putBoolean(script.absolutePath, !isPinned).apply()
                val msg = if (!isPinned) "Script pinned to top" else "Pin removed"
                Toast.makeText(this@ScriptManagerActivity, msg, Toast.LENGTH_SHORT).show()
                filterScripts()
            }

            override fun onSchedule(script: File) {
                showScheduleDialog(script)
            }

            override fun onShare(script: File) {
                shareScript(script)
            }
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = scriptAdapter
    }

    private fun showScheduleDialog(script: File) {
        val options = arrayOf(
            "Every 15 minutes (Periodic)",
            "Every 30 minutes (Periodic)",
            "Every 1 hour (Periodic)",
            "Every 6 hours (Periodic)",
            "Every 24 hours (Daily)",
            "Run once in 5 minutes (One-Time)"
        )
        val intervals = longArrayOf(15L, 30L, 60L, 360L, 1440L, 5L)
        val isPeriodicArray = booleanArrayOf(true, true, true, true, true, false)

        MaterialAlertDialogBuilder(this)
            .setTitle("Schedule: ${script.name}")
            .setItems(options) { _, which ->
                val interval = intervals[which]
                val isPeriodic = isPeriodicArray[which]
                com.polymath.fs.core.ScriptScheduleManager.scheduleScript(
                    context = this,
                    scriptFile = script,
                    intervalMinutes = interval,
                    isPeriodic = isPeriodic
                )
                val typeStr = if (isPeriodic) "every $interval min(s)" else "in $interval min(s)"
                Toast.makeText(this, "Scheduled ${script.name} $typeStr via WorkManager", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareScript(script: File) {
        try {
            val content = script.readText()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/javascript"
                putExtra(Intent.EXTRA_SUBJECT, script.name)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share JavaScript Extension")
            startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share script: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadScripts() {
        allScriptFiles.clear()
        if (scriptsDir.exists()) {
            val files = scriptsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".js")
            }
            if (files != null) {
                allScriptFiles.addAll(files)
            }
        }
        filterScripts()
    }

    private fun filterScripts() {
        val pinsPref = getSharedPreferences("script_pins", Context.MODE_PRIVATE)

        val filtered = when (selectedCategory) {
            "All" -> allScriptFiles
            "Pinned" -> allScriptFiles.filter { pinsPref.getBoolean(it.absolutePath, false) }
            else -> allScriptFiles.filter { file ->
                val meta = BuiltInScriptManager.getScriptMetadata(file)
                meta.category.equals(selectedCategory, ignoreCase = true) ||
                    (selectedCategory == "Organizer" && meta.category.contains("Organizer", ignoreCase = true)) ||
                    (selectedCategory == "Security" && meta.category.contains("Vault", ignoreCase = true))
            }
        }

        // Sort: Pinned scripts first, then alphabetically by displayName
        val sorted = filtered.sortedWith(
            compareByDescending<File> { pinsPref.getBoolean(it.absolutePath, false) }
                .thenBy { it.name.lowercase() }
        )

        displayedScriptFiles.clear()
        displayedScriptFiles.addAll(sorted)
        scriptAdapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (displayedScriptFiles.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun runScript(script: File) {
        val app = application as PolymathApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scriptContent = script.readText()
                val output = app.jsBridge.executeScript(
                    script = scriptContent,
                    scriptName = script.name,
                    onAlert = { title, message ->
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this@ScriptManagerActivity)
                                .setTitle(title)
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    val displayMsg = if (output.isNotBlank() && output != "undefined") {
                        "Result:\n$output"
                    } else {
                        "Script ${script.name} executed successfully."
                    }
                    MaterialAlertDialogBuilder(this@ScriptManagerActivity)
                        .setTitle(script.name.removeSuffix(".js").replace("_", " "))
                        .setMessage(displayMsg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(this@ScriptManagerActivity)
                        .setTitle("Script Error")
                        .setMessage(e.message ?: "Failed to execute script")
                        .setPositiveButton("Dismiss", null)
                        .show()
                }
            }
        }
    }

    private fun editScript(script: File) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("path", script.absolutePath)
            putExtra("filePath", script.absolutePath)
        }
        startActivity(intent)
    }

    private fun showCreateScriptDialog() {
        val input = EditText(this).apply {
            hint = "myscript.js"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("New JavaScript Script")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (!name.endsWith(".js")) {
                        name = "$name.js"
                    }
                    val newFile = File(scriptsDir, name)
                    if (!newFile.exists()) {
                        val template = """
                            // Polymath JavaScript Script: $name
                            // Built-in APIs available:
                            // Polymath.fs.listDir(path)
                            // Polymath.fs.copy(srcArrayJson, dest)
                            // Polymath.fs.move(srcArrayJson, dest)
                            // Polymath.fs.delete(pathsArrayJson)
                            // Polymath.fs.mkdir(path)
                            // Polymath.fs.rename(oldPath, newName)
                            // Polymath.ui.showToast(message)
                            // PolymathOS.toast(message)
                            // PolymathOS.alert(title, message)
                            // PolymathOS.prompt(title, callback)
                            // PolymathOS.setTheme(themeJson)
                            // PolymathOS.daemonCommand(action, payload)

                            PolymathOS.toast("Executing $name...");
                            var items = JSON.parse(Polymath.fs.listDir("/storage/emulated/0"));
                            "Found " + items.length + " entries in storage root.";
                        """.trimIndent()
                        newFile.writeText(template)
                        loadScripts()
                        editScript(newFile)
                    } else {
                        Toast.makeText(this, "A script with this name already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_script_manager, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_bulk_execution -> {
                showBulkExecutionPicker()
                true
            }
            R.id.action_share_library -> {
                shareExtensionsLibrary()
                true
            }
            R.id.action_reload_builtin -> {
                BuiltInScriptManager.extractAllBuiltInScripts(this, forceOverwrite = true)
                loadScripts()
                Toast.makeText(this, "Built-in script library restored", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_new_script -> {
                showCreateScriptDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showBulkExecutionPicker() {
        val downloadDir = File("/storage/emulated/0/Download")
        val files = if (downloadDir.exists()) {
            downloadDir.listFiles { f -> f.isFile }?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }

        val sheet = BulkScriptExecutionBottomSheet()
        sheet.setSelectedFiles(files)
        sheet.show(supportFragmentManager, "BulkScriptExecutionBottomSheet")
    }

    private fun shareExtensionsLibrary() {
        if (allScriptFiles.isEmpty()) {
            Toast.makeText(this, "No scripts available to share", Toast.LENGTH_SHORT).show()
            return
        }
        val builder = StringBuilder()
        builder.append("// Polymath FS - Extensions Library Export\n")
        builder.append("// Total Scripts: ${allScriptFiles.size}\n\n")
        for (file in allScriptFiles) {
            builder.append("/* ==========================================\n")
            builder.append(" * SCRIPT: ${file.name}\n")
            builder.append(" * ========================================== */\n")
            try {
                builder.append(file.readText())
            } catch (e: Exception) {
                builder.append("// Error reading script: ${e.message}")
            }
            builder.append("\n\n")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Polymath FS Extension Library")
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        startActivity(Intent.createChooser(sendIntent, "Share Extension Library"))
    }
}
