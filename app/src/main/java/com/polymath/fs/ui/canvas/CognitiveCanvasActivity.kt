package com.polymath.fs.ui.canvas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.fs.R
import com.polymath.fs.core.SystemBarHelper
import com.polymath.fs.core.ThemeManager
import com.polymath.fs.databinding.ActivityCognitiveCanvasBinding
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.viewers.EditorActivity
import com.polymath.fs.viewmodels.CognitiveCanvasViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class CognitiveCanvasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCognitiveCanvasBinding
    private val viewModel: CognitiveCanvasViewModel by viewModels()
    private var currentDirectoryPath: String = ""
    private val PREF_NAME = "cognitive_canvas_prefs"
    private val PREF_KEY_COACH_MARK_SHOWN = "coach_mark_shown_v1"

    companion object {
        const val EXTRA_DIRECTORY_PATH = "extra_directory_path"

        fun start(context: Context, path: String? = null) {
            val intent = Intent(context, CognitiveCanvasActivity::class.java).apply {
                if (path != null) putExtra(EXTRA_DIRECTORY_PATH, path)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityCognitiveCanvasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Dynamically adjust status bar contrast for header component
        SystemBarHelper.adjustSystemBarContrastForHeader(this, binding.headerCard)

        // Handle edge-to-edge status bar padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.headerCard.setPadding(0, statusBarInsets.top / 2, 0, 0)
            insets
        }

        currentDirectoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH)
            ?: Environment.getExternalStorageDirectory().absolutePath

        setupUI()
        setupSearchBar()
        setupCoachMark()
        observeViewModel()
        viewModel.loadPath(currentDirectoryPath)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvCanvasSubtitle.text = currentDirectoryPath

        // Snap to grid toggle
        binding.btnSnapToGrid.setOnClickListener {
            binding.cognitiveCanvasView.isSnapToGridEnabled = !binding.cognitiveCanvasView.isSnapToGridEnabled
            val isEnabled = binding.cognitiveCanvasView.isSnapToGridEnabled
            binding.btnSnapToGrid.setBackgroundResource(
                if (isEnabled) R.drawable.bg_canvas_btn_active else 0
            )
            binding.btnSnapToGrid.setColorFilter(
                if (isEnabled) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            Toast.makeText(
                this,
                if (isEnabled) "Snap-to-Grid Enabled (Magnetic Precision)" else "Snap-to-Grid Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Smart Arrange force-directed clustering button
        binding.btnSmartArrange.setOnClickListener {
            Toast.makeText(this, "Smart Arranging files by metadata & directory clusters...", Toast.LENGTH_SHORT).show()
            binding.cognitiveCanvasView.smartArrange {
                viewModel.persistAllNodePositions()
                Toast.makeText(this, "Spatial arrangement organized and saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Link mode toggle
        binding.btnLinkMode.setOnClickListener {
            binding.cognitiveCanvasView.isLinkModeActive = !binding.cognitiveCanvasView.isLinkModeActive
            val isLinkActive = binding.cognitiveCanvasView.isLinkModeActive
            binding.btnLinkMode.setBackgroundResource(
                if (isLinkActive) R.drawable.bg_canvas_btn_active else 0
            )
            binding.btnLinkMode.setColorFilter(
                if (isLinkActive) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            binding.tvStatusHelp.text = if (isLinkActive) {
                "🔗 Link Mode: Drag from one file to another to create a relationship"
            } else {
                "💡 Long-press any file for Quick Actions (Color-Code, Rename, Move, Delete)"
            }
        }

        // Reset camera / center
        binding.btnResetView.setOnClickListener {
            binding.cognitiveCanvasView.resetViewAnimated()
        }

        // Help button opens coach mark guide
        binding.btnHelp.setOnClickListener {
            showCoachMark()
        }

        // Handle user manual node move -> persist to Room
        binding.cognitiveCanvasView.onNodeMovedListener = { movedNode ->
            viewModel.persistNodePosition(movedNode, currentDirectoryPath)
        }

        // Handle user link drawn between nodes -> persist to Room
        binding.cognitiveCanvasView.onNodesLinkedListener = { source, target ->
            viewModel.linkNodes(source, target, "User Link")
            Toast.makeText(this, "Linked ${source.fileNode.name} to ${target.fileNode.name}", Toast.LENGTH_SHORT).show()
        }

        // Double-click node to open
        binding.cognitiveCanvasView.onNodeDoubleClickListener = { node ->
            openFileOrDirectory(node)
        }

        // Long-press on node -> Context Menu Quick Actions
        binding.cognitiveCanvasView.onNodeLongClickListener = { node ->
            showNodeContextMenu(node)
        }
    }

    private fun setupSearchBar() {
        // Toggle search bar visibility
        binding.btnSearchToggle.setOnClickListener {
            val isVisible = binding.layoutSearchBar.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutSearchBar.visibility = View.GONE
                binding.etCanvasSearch.setText("")
                hideKeyboard(binding.etCanvasSearch)
                binding.btnSearchToggle.setColorFilter(Color.parseColor("#94A3B8"))
            } else {
                binding.layoutSearchBar.visibility = View.VISIBLE
                binding.btnSearchToggle.setColorFilter(Color.parseColor("#38BDF8"))
                binding.etCanvasSearch.requestFocus()
                showKeyboard(binding.etCanvasSearch)
            }
        }

        binding.etCanvasSearch.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            binding.cognitiveCanvasView.searchAndHighlight(query)
        }

        binding.cognitiveCanvasView.onSearchResultsChanged = { query, count, currentIndex ->
            if (query.isEmpty() || count == 0) {
                binding.tvSearchResultCount.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvSearchResultCount.text = if (query.isNotEmpty()) "0 found" else ""
                binding.btnSearchPrev.visibility = View.GONE
                binding.btnSearchNext.visibility = View.GONE
            } else {
                binding.tvSearchResultCount.visibility = View.VISIBLE
                binding.tvSearchResultCount.text = "${currentIndex + 1}/$count"
                binding.btnSearchPrev.visibility = if (count > 1) View.VISIBLE else View.GONE
                binding.btnSearchNext.visibility = if (count > 1) View.VISIBLE else View.GONE
            }
        }

        binding.btnSearchNext.setOnClickListener {
            binding.cognitiveCanvasView.focusNextSearchResult()
        }

        binding.btnSearchPrev.setOnClickListener {
            binding.cognitiveCanvasView.focusPreviousSearchResult()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etCanvasSearch.setText("")
        }
    }

    private fun setupCoachMark() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hasShown = prefs.getBoolean(PREF_KEY_COACH_MARK_SHOWN, false)

        if (!hasShown) {
            showCoachMark()
        }

        binding.btnDismissCoachMark.setOnClickListener {
            dismissCoachMark()
        }

        binding.coachMarkOverlay.setOnClickListener {
            dismissCoachMark()
        }
    }

    private fun showCoachMark() {
        binding.coachMarkOverlay.alpha = 0f
        binding.coachMarkOverlay.visibility = View.VISIBLE
        binding.coachMarkOverlay.animate()
            .alpha(1f)
            .setDuration(280)
            .start()
    }

    private fun dismissCoachMark() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_KEY_COACH_MARK_SHOWN, true).apply()

        binding.coachMarkOverlay.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                binding.coachMarkOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.canvas.nodes.isNotEmpty()) {
                    binding.cognitiveCanvasView.setGraphData(
                        state.canvas.nodes,
                        state.canvas.edges,
                        autoArrange = false
                    )
                }
            }
        }
    }

    private fun showNodeContextMenu(node: CanvasNode) {
        val file = File(node.fileNode.path)
        val isDir = file.isDirectory
        val title = (if (isDir) "📁 " else "📄 ") + file.name

        val actions = arrayOf(
            if (isDir) "Open Directory" else "Open File",
            "🎨 Set Theme Color",
            "Rename",
            "Move",
            if (node.isPinned) "Unpin Position" else "Pin Position",
            "Delete"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> openFileOrDirectory(node)
                    1 -> showThemeColorPicker(node)
                    2 -> promptRenameFile(node)
                    3 -> promptMoveFile(node)
                    4 -> togglePinNode(node)
                    5 -> confirmDeleteFile(node)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeColorPicker(node: CanvasNode) {
        val colorOptions = arrayOf(
            "Default (Reset Color)",
            "Sky Blue (#38BDF8)",
            "Emerald Green (#10B981)",
            "Violet Purple (#A855F7)",
            "Amber Gold (#F59E0B)",
            "Rose Crimson (#F43F5E)",
            "Indigo (#6366F1)",
            "Cyan (#06B6D4)",
            "Slate (#64748B)"
        )

        val colorValues: Array<Int?> = arrayOf(
            null,
            Color.parseColor("#38BDF8"),
            Color.parseColor("#10B981"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#F43F5E"),
            Color.parseColor("#6366F1"),
            Color.parseColor("#06B6D4"),
            Color.parseColor("#64748B")
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Theme Color for '${node.fileNode.name}'")
            .setItems(colorOptions) { _, which ->
                val selectedColor = colorValues[which]
                viewModel.updateNodeThemeColor(node, selectedColor, currentDirectoryPath)
                binding.cognitiveCanvasView.invalidate()
                val colorName = colorOptions[which]
                Toast.makeText(this, "Theme color updated: $colorName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFileOrDirectory(node: CanvasNode) {
        val file = File(node.fileNode.path)
        if (file.isDirectory) {
            // Load canvas into directory
            currentDirectoryPath = file.absolutePath
            binding.tvCanvasSubtitle.text = currentDirectoryPath
            viewModel.loadPath(currentDirectoryPath)
        } else {
            // Open in EditorActivity or default viewer
            try {
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra("path", file.absolutePath)
                    putExtra("filePath", file.absolutePath)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to system intent
                val uri = try {
                    FileProvider.getUriForFile(this, "${packageName}.provider", file)
                } catch (ex: Exception) {
                    Uri.fromFile(file)
                }
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(viewIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptRenameFile(node: CanvasNode) {
        val editText = EditText(this).apply {
            setText(node.fileNode.name)
            setSelection(node.fileNode.name.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename File")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != node.fileNode.name) {
                    viewModel.renameFile(node, newName) { success, msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptMoveFile(node: CanvasNode) {
        val editText = EditText(this).apply {
            hint = "Destination directory path"
            setText(File(node.fileNode.path).parent ?: currentDirectoryPath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Move '${node.fileNode.name}'")
            .setView(editText)
            .setPositiveButton("Move") { _, _ ->
                val destPath = editText.text.toString().trim()
                if (destPath.isNotEmpty()) {
                    viewModel.moveFile(node, destPath) { success, msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun togglePinNode(node: CanvasNode) {
        node.isPinned = !node.isPinned
        viewModel.persistNodePosition(node, currentDirectoryPath)
        binding.cognitiveCanvasView.invalidate()
        Toast.makeText(
            this,
            if (node.isPinned) "Node pinned at (${node.x.toInt()}, ${node.y.toInt()})" else "Node unpinned",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmDeleteFile(node: CanvasNode) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete '${node.fileNode.name}'?")
            .setMessage("This action will permanently delete this file from storage.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFile(node) { success, msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
