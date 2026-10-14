package com.polymath.fs.ui.clips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.SystemBarHelper
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.db.dao.CognitiveClipDao
import com.polymath.fs.data.db.entities.CognitiveClipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CognitiveClipsActivity : BaseDynamicActivity() {

    private lateinit var clipDao: CognitiveClipDao
    private lateinit var clipAdapter: CognitiveClipAdapter
    private val searchQuery = MutableStateFlow("")

    private lateinit var headerLayout: View
    private lateinit var tvClipCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var rvClips: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var btnEmptyCreate: MaterialButton

    private var currentClipsList: List<CognitiveClipEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cognitive_clips)

        clipDao = AppDatabase.getDatabase(applicationContext).cognitiveClipDao()

        initViews()
        setupInsets()
        setupRecyclerView()
        setupSearch()
        setupActions()
        observeClips()
    }

    private fun initViews() {
        headerLayout = findViewById(R.id.header_layout)
        tvClipCount = findViewById(R.id.tv_clip_count)
        etSearch = findViewById(R.id.et_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        rvClips = findViewById(R.id.rv_clips)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        btnEmptyCreate = findViewById(R.id.btn_empty_create)
    }

    private fun setupInsets() {
        SystemBarHelper.applyDynamicStatusBarInsets(headerLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rvClips) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navBars.bottom + 48
            )
            insets
        }
    }

    private fun setupRecyclerView() {
        clipAdapter = CognitiveClipAdapter(
            onClipClick = { clip -> showViewClipDialog(clip) },
            onEditClick = { clip -> showEditClipDialog(clip) },
            onDeleteClick = { clip -> confirmDeleteClip(clip) },
            onPinToggle = { clip -> togglePinClip(clip) }
        )
        rvClips.layoutManager = LinearLayoutManager(this)
        rvClips.adapter = clipAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                searchQuery.value = query
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btn_export).setOnClickListener {
            exportClips()
        }

        findViewById<ImageButton>(R.id.btn_clear_all).setOnClickListener {
            confirmClearAll()
        }

        findViewById<MaterialButton>(R.id.btn_new_clip).setOnClickListener {
            showEditClipDialog(null)
        }

        btnEmptyCreate.setOnClickListener {
            showEditClipDialog(null)
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeClips() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchQuery
                    .debounce(150)
                    .flatMapLatest { query ->
                        if (query.isBlank()) {
                            clipDao.getAllClips()
                        } else {
                            clipDao.searchClips(query.trim())
                        }
                    }
                    .flowOn(Dispatchers.IO)
                    .collect { clips ->
                        currentClipsList = clips
                        clipAdapter.submitList(clips)
                        updateUiState(clips)
                    }
            }
        }
    }

    private fun updateUiState(clips: List<CognitiveClipEntity>) {
        val isSearching = searchQuery.value.isNotBlank()
        tvClipCount.text = if (isSearching) {
            "${clips.size} matching research clips"
        } else {
            "${clips.size} clips stored"
        }

        if (clips.isEmpty()) {
            rvClips.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_empty_title).text = if (isSearching) "No Matching Clips" else "No Cognitive Clips"
            findViewById<TextView>(R.id.tv_empty_desc).text = if (isSearching) {
                "No clips matched '${searchQuery.value}'. Try another keyword."
            } else {
                "Double-tap the floating droplet anywhere to instantly capture research notes to your cognitive store.\n\nTap and hold any clip to copy to clipboard for pasting."
            }
        } else {
            rvClips.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun showViewClipDialog(clip: CognitiveClipEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_clip, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_view_title)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tv_view_meta)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_view_content)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btn_view_copy)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btn_view_share)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_view_close)

        tvTitle.text = clip.title.ifBlank { "Untitled Clip" }
        tvContent.text = clip.content

        val words = if (clip.content.isBlank()) 0 else clip.content.trim().split("\\s+".toRegex()).size
        val lines = clip.content.lines().size
        val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(clip.timestamp))
        val pinStr = if (clip.isPinned) " • [Pinned]" else ""
        tvMeta.text = "Captured $dateStr • ${clip.charCount} chars • $words words • $lines lines$pinStr"

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Cognitive Clip", clip.content)
            clipboard.setPrimaryClip(clipData)
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(this, "Copied to clipboard for pasting!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, clip.title)
                putExtra(Intent.EXTRA_TEXT, clip.content)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Cognitive Clip"))
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditClipDialog(clip: CognitiveClipEntity?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_clip, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.et_clip_title)
        val etContent = dialogView.findViewById<TextInputEditText>(R.id.et_clip_content)
        val etTags = dialogView.findViewById<TextInputEditText>(R.id.et_clip_tags)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_clip_cancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_clip_save)

        if (clip != null) {
            tvDialogTitle.text = "Edit Cognitive Clip"
            etTitle.setText(clip.title)
            etContent.setText(clip.content)
            etTags.setText(clip.tags)
        } else {
            tvDialogTitle.text = "New Cognitive Clip"
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val content = etContent.text?.toString()?.trim() ?: ""
            if (content.isEmpty()) {
                Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rawTitle = etTitle.text?.toString()?.trim() ?: ""
            val title = if (rawTitle.isNotEmpty()) {
                rawTitle
            } else {
                val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Clip"
                if (firstLine.length > 50) firstLine.take(47) + "..." else firstLine
            }
            val tags = etTags.text?.toString()?.trim() ?: ""

            lifecycleScope.launch(Dispatchers.IO) {
                if (clip != null) {
                    val updated = clip.copy(
                        title = title,
                        content = content,
                        charCount = content.length,
                        tags = tags
                    )
                    clipDao.update(updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CognitiveClipsActivity, "Clip updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val newClip = CognitiveClipEntity(
                        title = title,
                        content = content,
                        charCount = content.length,
                        tags = tags,
                        timestamp = System.currentTimeMillis()
                    )
                    clipDao.insert(newClip)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CognitiveClipsActivity, "Clip saved (${content.length} chars)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmDeleteClip(clip: CognitiveClipEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Clip?")
            .setMessage("Are you sure you want to delete '${clip.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    clipDao.delete(clip)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CognitiveClipsActivity, "Clip deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun togglePinClip(clip: CognitiveClipEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            clipDao.update(clip.copy(isPinned = !clip.isPinned))
            withContext(Dispatchers.Main) {
                val msg = if (!clip.isPinned) "Clip pinned" else "Clip unpinned"
                Toast.makeText(this@CognitiveClipsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmClearAll() {
        if (currentClipsList.isEmpty()) {
            Toast.makeText(this, "No clips to clear", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Clear All Clips?")
            .setMessage("This will permanently remove all ${currentClipsList.size} cognitive research clips. This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    clipDao.clearAll()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CognitiveClipsActivity, "All clips cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportClips() {
        if (currentClipsList.isEmpty()) {
            Toast.makeText(this, "No clips to export", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sb = StringBuilder()
            sb.append("# Polymath Cognitive Research Clips\n")
            sb.append("*Exported on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}*\n")
            sb.append("*Total Clips: ${currentClipsList.size}*\n\n")

            currentClipsList.forEachIndexed { index, clip ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(clip.timestamp))
                sb.append("## ${index + 1}. ${clip.title}\n")
                sb.append("- **Date:** $dateStr\n")
                sb.append("- **Characters:** ${clip.charCount}\n")
                if (clip.tags.isNotBlank()) {
                    sb.append("- **Tags:** ${clip.tags}\n")
                }
                if (clip.isPinned) {
                    sb.append("- **Status:** Pinned\n")
                }
                sb.append("\n```\n")
                sb.append(clip.content)
                sb.append("\n```\n\n---\n\n")
            }

            val exportText = sb.toString()

            // Save to external storage if possible, fallback to app storage
            val targetDir = File("/storage/emulated/0/Polymath")
            val targetFile = if (targetDir.exists() || targetDir.mkdirs()) {
                File(targetDir, "CognitiveClips_$timestamp.md")
            } else {
                File(getExternalFilesDir(null) ?: filesDir, "CognitiveClips_$timestamp.md")
            }

            try {
                targetFile.writeText(exportText)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CognitiveClipsActivity, "Exported ${currentClipsList.size} clips to ${targetFile.name}", Toast.LENGTH_LONG).show()

                // Also trigger Share Intent so user can immediately send to notes/chat
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_SUBJECT, "Polymath Cognitive Clips Export")
                    putExtra(Intent.EXTRA_TEXT, exportText)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Cognitive Clips Export"))
            }
        }
    }
}
