package com.polymath.fs.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.polymath.fs.MainActivity
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BuiltInScriptManager
import com.polymath.fs.core.ScriptScheduleItem
import com.polymath.fs.core.ScriptScheduleManager
import com.polymath.fs.core.StorageTelemetryManager
import com.polymath.fs.viewers.EditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeDashboardFragment : Fragment() {

    private lateinit var progressStorage: LinearProgressIndicator
    private lateinit var tvStoragePercent: TextView
    private lateinit var tvStorageUsed: TextView
    private lateinit var tvStorageFree: TextView
    private lateinit var tvInternalStat: TextView
    private lateinit var tvRootStat: TextView

    private lateinit var tvJsEngineCount: TextView
    private lateinit var tvJsCategoriesBreakdown: TextView
    private lateinit var rvTopScripts: RecyclerView
    private lateinit var btnBrowseAllScripts: MaterialButton

    private lateinit var tvScheduledCount: TextView
    private lateinit var layoutSchedulesList: LinearLayout
    private lateinit var tvNoSchedules: TextView
    private lateinit var btnScheduleNewScript: MaterialButton

    private lateinit var tvKernelRelease: TextView
    private lateinit var tvKernelCpu: TextView
    private lateinit var tvKernelLoad: TextView
    private lateinit var tvKernelMem: TextView
    private lateinit var tvKernelState: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        loadStorageTelemetry()
        loadKernelTelemetry()
        setupDirectoryShortcuts(view)
        loadJsEngineOverview()
        loadScheduledAutomations()
        setupToolsLaunchpad(view)
    }

    override fun onResume() {
        super.onResume()
        loadStorageTelemetry()
        loadKernelTelemetry()
        loadJsEngineOverview()
        loadScheduledAutomations()
    }

    private fun initViews(view: View) {
        progressStorage = view.findViewById(R.id.progress_storage)
        tvStoragePercent = view.findViewById(R.id.tv_storage_percent)
        tvStorageUsed = view.findViewById(R.id.tv_storage_used)
        tvStorageFree = view.findViewById(R.id.tv_storage_free)
        tvInternalStat = view.findViewById(R.id.tv_internal_stat)
        tvRootStat = view.findViewById(R.id.tv_root_stat)

        tvJsEngineCount = view.findViewById(R.id.tv_js_engine_count)
        tvJsCategoriesBreakdown = view.findViewById(R.id.tv_js_categories_breakdown)
        rvTopScripts = view.findViewById(R.id.rv_top_scripts)
        btnBrowseAllScripts = view.findViewById(R.id.btn_browse_all_scripts)

        tvScheduledCount = view.findViewById(R.id.tv_scheduled_count)
        layoutSchedulesList = view.findViewById(R.id.layout_schedules_list)
        tvNoSchedules = view.findViewById(R.id.tv_no_schedules)
        btnScheduleNewScript = view.findViewById(R.id.btn_schedule_new_script)

        tvKernelRelease = view.findViewById(R.id.tv_kernel_release)
        tvKernelCpu = view.findViewById(R.id.tv_kernel_cpu)
        tvKernelLoad = view.findViewById(R.id.tv_kernel_load)
        tvKernelMem = view.findViewById(R.id.tv_kernel_mem)
        tvKernelState = view.findViewById(R.id.tv_kernel_state)

        btnBrowseAllScripts.setOnClickListener {
            startActivity(Intent(requireContext(), ScriptManagerActivity::class.java))
        }

        btnScheduleNewScript.setOnClickListener {
            startActivity(Intent(requireContext(), ScriptManagerActivity::class.java))
        }
    }

    private fun loadStorageTelemetry() {
        try {
            val report = StorageTelemetryManager.getStorageTelemetry(requireContext())

            progressStorage.progress = report.primaryUsedPercent
            tvStoragePercent.text = "${report.primaryUsedPercent}% Used"
            tvStorageUsed.text = "Used: ${StorageTelemetryManager.formatBytes(report.primaryUsedBytes)} / ${StorageTelemetryManager.formatBytes(report.primaryTotalBytes)}"
            tvStorageFree.text = "Free: ${StorageTelemetryManager.formatBytes(report.primaryFreeBytes)}"

            tvInternalStat.text = "Free: ${StorageTelemetryManager.formatBytes(report.internalFreeBytes)} (${StorageTelemetryManager.formatBytes(report.internalTotalBytes)})"

            val systemDesc = if (report.systemIsReadOnly) {
                "Read-Only (${StorageTelemetryManager.formatBytes(report.systemTotalBytes)})"
            } else {
                "Free: ${StorageTelemetryManager.formatBytes(report.systemFreeBytes)}"
            }
            tvRootStat.text = systemDesc
        } catch (e: Exception) {
            tvStoragePercent.text = "Storage Available"
        }
    }

    private fun loadKernelTelemetry() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val kernelController = com.polymath.fs.core.KernelEngineController()
                val telemetrics = kernelController.getKernelTelemetry()
                withContext(Dispatchers.Main) {
                    tvKernelRelease.text = telemetrics.kernelRelease
                    tvKernelCpu.text = "${telemetrics.cpuInfo.cores} Cores @ ${telemetrics.cpuInfo.architecture}"
                    val (l1, l5, l15) = telemetrics.loadAverages
                    tvKernelLoad.text = String.format(java.util.Locale.US, "%.2f, %.2f, %.2f", l1, l5, l15)
                    
                    val availMb = telemetrics.memInfo.availableMemKb / 1024
                    val totalMb = telemetrics.memInfo.totalMemKb / 1024
                    if (totalMb > 0) {
                        tvKernelMem.text = "${availMb}MB avail / ${totalMb}MB"
                    } else {
                        tvKernelMem.text = "Active Linux Mem"
                    }

                    val seLinuxStr = if (telemetrics.isSeLinuxEnforcing) "Enforcing" else "Permissive"
                    tvKernelState.text = "${telemetrics.cpuInfo.governor} | $seLinuxStr"
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun setupDirectoryShortcuts(view: View) {
        val shortcuts = mapOf(
            R.id.dir_shortcut_download to "/storage/emulated/0/Download",
            R.id.dir_shortcut_documents to "/storage/emulated/0/Documents",
            R.id.dir_shortcut_pictures to "/storage/emulated/0/Pictures",
            R.id.dir_shortcut_music to "/storage/emulated/0/Music",
            R.id.dir_shortcut_movies to "/storage/emulated/0/Movies",
            R.id.dir_shortcut_scripts to BuiltInScriptManager.getScriptsDirectory(requireContext()).absolutePath,
            R.id.dir_shortcut_root to "/",
            R.id.dir_shortcut_primary to "/storage/emulated/0",
            R.id.dir_shortcut_android to "/storage/emulated/0/Android"
        )

        for ((viewId, path) in shortcuts) {
            view.findViewById<View>(viewId)?.setOnClickListener {
                (activity as? MainActivity)?.navigateToDirectory(path)
            }
        }
    }

    private fun loadJsEngineOverview() {
        val scriptsDir = BuiltInScriptManager.getScriptsDirectory(requireContext())
        val scriptFiles = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".js") }?.toList() ?: emptyList()

        tvJsEngineCount.text = "${scriptFiles.size} Extensions"

        val categories = scriptFiles.map { BuiltInScriptManager.getScriptMetadata(it).category }
        val counts = categories.groupingBy { it }.eachCount()
        val breakdown = counts.entries.joinToString(" • ") { "${it.key} (${it.value})" }
        tvJsCategoriesBreakdown.text = if (breakdown.isNotBlank()) breakdown else "JavaScript QuickJS Extensions Active"

        val pinsPref = requireContext().getSharedPreferences("script_pins", Context.MODE_PRIVATE)
        val sortedScripts = scriptFiles.sortedWith(
            compareByDescending<File> { pinsPref.getBoolean(it.absolutePath, false) }
                .thenBy { it.name.lowercase() }
        ).take(8)

        rvTopScripts.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvTopScripts.adapter = DashboardScriptsAdapter(sortedScripts) { script ->
            runScriptDirect(script)
        }
    }

    private fun runScriptDirect(scriptFile: File) {
        val app = requireActivity().application as PolymathApp
        Toast.makeText(context, "Executing ${scriptFile.name}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scriptContent = scriptFile.readText()
                val result = app.jsBridge.executeScript(
                    script = scriptContent,
                    scriptName = scriptFile.name,
                    onAlert = { alertTitle, alertMsg ->
                        activity?.runOnUiThread {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(if (alertTitle.isNotEmpty()) alertTitle else "Script Alert")
                                .setMessage(alertMsg)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    val msg = if (result.isNotBlank() && result != "undefined") "Result: $result" else "${scriptFile.name} completed successfully."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Execution Failed")
                        .setMessage(e.message ?: "Script error")
                        .setPositiveButton("Dismiss", null)
                        .show()
                }
            }
        }
    }

    private fun loadScheduledAutomations() {
        val schedules = ScriptScheduleManager.getAllSchedules(requireContext())
        tvScheduledCount.text = "${schedules.size} Active"

        layoutSchedulesList.removeAllViews()
        if (schedules.isEmpty()) {
            tvNoSchedules.visibility = View.VISIBLE
            layoutSchedulesList.visibility = View.GONE
        } else {
            tvNoSchedules.visibility = View.GONE
            layoutSchedulesList.visibility = View.VISIBLE

            for (schedule in schedules) {
                val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_dashboard_schedule, layoutSchedulesList, false)
                val tvName = itemView.findViewById<TextView>(R.id.tv_schedule_name)
                val tvInterval = itemView.findViewById<TextView>(R.id.tv_schedule_interval)
                val btnCancel = itemView.findViewById<MaterialButton>(R.id.btn_cancel_schedule)

                tvName.text = schedule.scriptName
                val freq = if (schedule.isPeriodic) "Every ${schedule.intervalMinutes} min(s) • Periodic" else "Run in ${schedule.intervalMinutes} min(s) • One-Time"
                tvInterval.text = "$freq • Status: Running in background"

                btnCancel.setOnClickListener {
                    ScriptScheduleManager.cancelSchedule(requireContext(), schedule.id)
                    loadScheduledAutomations()
                    Toast.makeText(context, "Cancelled automation for ${schedule.scriptName}", Toast.LENGTH_SHORT).show()
                }

                layoutSchedulesList.addView(itemView)
            }
        }
    }

    private fun setupToolsLaunchpad(view: View) {
        view.findViewById<MaterialButton>(R.id.btn_launch_editor)?.setOnClickListener {
            startActivity(Intent(requireContext(), EditorActivity::class.java))
        }

        view.findViewById<MaterialButton>(R.id.btn_launch_terminal)?.setOnClickListener {
            startActivity(Intent(requireContext(), TerminalActivity::class.java))
        }
    }

    private class DashboardScriptsAdapter(
        private val scripts: List<File>,
        private val onRunClick: (File) -> Unit
    ) : RecyclerView.Adapter<DashboardScriptsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_script, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = scripts[position]
            val meta = BuiltInScriptManager.getScriptMetadata(file)
            holder.tvName.text = meta.displayName
            holder.tvCategory.text = meta.category
            holder.tvIcon.text = when (meta.category) {
                "Organizer", "AutoOrganizer" -> "🗂️"
                "Security", "GhostVault" -> "🛡️"
                "Themes" -> "🎨"
                "Network" -> "🌐"
                "Utils" -> "⚙️"
                "Core" -> "⚡"
                else -> "📜"
            }
            holder.btnRun.setOnClickListener { onRunClick(file) }
        }

        override fun getItemCount(): Int = scripts.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView = v.findViewById(R.id.tv_script_icon)
            val tvName: TextView = v.findViewById(R.id.tv_script_name)
            val tvCategory: TextView = v.findViewById(R.id.tv_script_category)
            val btnRun: MaterialButton = v.findViewById(R.id.btn_run_script)
        }
    }
}
