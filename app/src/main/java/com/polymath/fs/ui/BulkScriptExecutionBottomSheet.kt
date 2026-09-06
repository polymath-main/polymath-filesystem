package com.polymath.fs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BuiltInScriptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BulkScriptExecutionBottomSheet : BottomSheetDialogFragment() {

    private var targetFiles: List<String> = emptyList()
    private val availableScripts = mutableListOf<File>()
    private var selectedScriptIndex: Int = 0

    fun setSelectedFiles(files: List<String>) {
        this.targetFiles = files
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_bulk_script, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvInfo = view.findViewById<TextView>(R.id.tv_selected_files_info)
        val rvScripts = view.findViewById<RecyclerView>(R.id.rv_available_scripts)
        val btnRun = view.findViewById<MaterialButton>(R.id.btn_run_bulk)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val progressBar = view.findViewById<ProgressBar>(R.id.bulk_progress)

        tvInfo.text = "Executing against ${targetFiles.size} selected file(s)"

        val scriptsDir = BuiltInScriptManager.getScriptsDirectory(requireContext())
        val files = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".js") }
        if (files != null) {
            availableScripts.addAll(files.sortedBy { it.name.lowercase() })
        }

        val adapter = ScriptSelectAdapter(availableScripts) { selectedIdx ->
            selectedScriptIndex = selectedIdx
        }

        rvScripts.layoutManager = LinearLayoutManager(requireContext())
        rvScripts.adapter = adapter

        btnCancel.setOnClickListener { dismiss() }

        btnRun.setOnClickListener {
            if (availableScripts.isEmpty()) {
                Toast.makeText(context, "No scripts available to run", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val chosenScript = availableScripts[selectedScriptIndex]
            runBulkScript(chosenScript, progressBar, btnRun)
        }
    }

    private fun runBulkScript(scriptFile: File, progressBar: ProgressBar, btnRun: MaterialButton) {
        val app = requireActivity().application as PolymathApp
        progressBar.visibility = View.VISIBLE
        btnRun.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scriptContent = scriptFile.readText()
                val output = app.jsBridge.executeScript(
                    script = scriptContent,
                    scriptName = scriptFile.name,
                    onAlert = null,
                    selectedFiles = targetFiles
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnRun.isEnabled = true
                    dismiss()

                    val displayMsg = if (output.isNotBlank() && output != "undefined") {
                        "Result:\n$output"
                    } else {
                        "Bulk execution of ${scriptFile.name} completed against ${targetFiles.size} items."
                    }

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Bulk Execution Finished")
                        .setMessage(displayMsg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnRun.isEnabled = true
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Bulk Execution Error")
                        .setMessage(e.message ?: "Failed to execute script")
                        .setPositiveButton("Dismiss", null)
                        .show()
                }
            }
        }
    }

    private class ScriptSelectAdapter(
        private val scripts: List<File>,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<ScriptSelectAdapter.ViewHolder>() {

        private var selectedPosition = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script_select, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = scripts[position]
            val info = BuiltInScriptManager.getScriptMetadata(file)
            holder.tvName.text = info.displayName
            holder.tvCategory.text = info.category
            holder.rbSelected.isChecked = position == selectedPosition

            holder.itemView.setOnClickListener {
                val oldPos = selectedPosition
                selectedPosition = holder.adapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                onSelect(selectedPosition)
            }
        }

        override fun getItemCount() = scripts.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val rbSelected: RadioButton = v.findViewById(R.id.rb_selected)
            val tvName: TextView = v.findViewById(R.id.tv_script_name)
            val tvCategory: TextView = v.findViewById(R.id.tv_script_category)
        }
    }
}
