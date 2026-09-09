package com.polymath.fs.ui.canvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
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
import com.polymath.fs.domain.canvas.models.CanvasAction
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.domain.canvas.models.CanvasRelationType
import com.polymath.fs.ui.canvas.preview.CanvasFilePreviewPanelController
import com.polymath.fs.viewers.EditorActivity
import com.polymath.fs.viewmodels.CognitiveCanvasViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CognitiveCanvasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCognitiveCanvasBinding
    private val viewModel: CognitiveCanvasViewModel by viewModels()
    private lateinit var previewPanelController: CanvasFilePreviewPanelController
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

        // Visual grid overlay toggle
        binding.btnVisualGrid.setOnClickListener {
            viewModel.toggleVisualGridOverlay()
            val isVisible = viewModel.uiState.value.isVisualGridOverlayVisible
            binding.cognitiveCanvasView.isVisualGridOverlayVisible = isVisible
            binding.btnVisualGrid.setBackgroundResource(
                if (isVisible) R.drawable.bg_canvas_btn_active else 0
            )
            binding.btnVisualGrid.setColorFilter(
                if (isVisible) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            Toast.makeText(
                this,
                if (isVisible) "Visual Grid Overlay On" else "Visual Grid Overlay Off",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Undo action
        binding.btnUndo.setOnClickListener {
            viewModel.undoAction { action ->
                if (action != null) {
                    binding.cognitiveCanvasView.invalidate()
                    Toast.makeText(this, "Undo: ${action.description}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Redo action
        binding.btnRedo.setOnClickListener {
            viewModel.redoAction { action ->
                if (action != null) {
                    binding.cognitiveCanvasView.invalidate()
                    Toast.makeText(this, "Redo: ${action.description}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Export Canvas Layout as Image
        binding.btnExportImage.setOnClickListener {
            exportCanvasAsImage()
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
                "🔗 Link Mode: Drag from one node to another to create relationship"
            } else {
                "💡 Long-press any file for Quick Actions (Color-Code, Rename, Move, Delete)"
            }
        }

        // Floating Precision Scale Slider & Indicator
        setupFloatingZoomSlider()

        // Reset camera / center
        binding.btnResetView.setOnClickListener {
            binding.cognitiveCanvasView.resetViewAnimated()
        }

        // Help button opens coach mark guide
        binding.btnHelp.setOnClickListener {
            showCoachMark()
        }

        // Handle user manual node move with initial position for Undo tracking -> persist to Room
        binding.cognitiveCanvasView.onNodeMovedWithInitialPositionListener = { movedNode, oldX, oldY ->
            viewModel.recordNodeMove(movedNode, oldX, oldY)
            viewModel.persistNodePosition(movedNode, currentDirectoryPath)
            if (::previewPanelController.isInitialized && previewPanelController.isShowing() &&
                previewPanelController.getCurrentNode()?.id == movedNode.id) {
                previewPanelController.updateSpatialCoordinates(movedNode.x, movedNode.y)
            }
        }
        binding.cognitiveCanvasView.onNodeMovedListener = { movedNode ->
            viewModel.persistNodePosition(movedNode, currentDirectoryPath)
            if (::previewPanelController.isInitialized && previewPanelController.isShowing() &&
                previewPanelController.getCurrentNode()?.id == movedNode.id) {
                previewPanelController.updateSpatialCoordinates(movedNode.x, movedNode.y)
            }
        }

        // Handle user link drawn between nodes -> prompt for Parent-to-Parent, Parent-to-Child, or Custom Link
        binding.cognitiveCanvasView.onNodesLinkedListener = { source, target ->
            promptLinkRelationshipType(source, target)
        }

        // Double-click node to open
        binding.cognitiveCanvasView.onNodeDoubleClickListener = { node ->
            openFileOrDirectory(node)
        }

        // Long-press on node -> Context Menu Quick Actions
        binding.cognitiveCanvasView.onNodeLongClickListener = { node ->
            showNodeContextMenu(node)
        }

        // Initialize expandable bottom sheet preview panel
        previewPanelController = CanvasFilePreviewPanelController(
            binding = binding.previewPanel,
            context = this,
            scope = lifecycleScope,
            onOpenFile = { node -> openFileOrDirectory(node) },
            onColorTag = { node -> showThemeColorPicker(node) },
            onPanelDismissed = {
                binding.cognitiveCanvasView.clearSelection()
            }
        )

        // When user selects a node on the canvas -> slide up the expandable preview panel
        binding.cognitiveCanvasView.onNodeSelectedListener = { selectedNode ->
            previewPanelController.showPreview(selectedNode)
        }

        // When selection is cleared on empty canvas tap -> hide preview panel
        binding.cognitiveCanvasView.onSelectionClearedListener = {
            previewPanelController.hidePreview()
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

                // Update Undo/Redo button states and alphas
                binding.btnUndo.isEnabled = state.canUndo
                binding.btnUndo.alpha = if (state.canUndo) 1.0f else 0.35f
                binding.btnRedo.isEnabled = state.canRedo
                binding.btnRedo.alpha = if (state.canRedo) 1.0f else 0.35f

                // Update Visual grid overlay
                binding.cognitiveCanvasView.isVisualGridOverlayVisible = state.isVisualGridOverlayVisible
                binding.btnVisualGrid.setBackgroundResource(
                    if (state.isVisualGridOverlayVisible) R.drawable.bg_canvas_btn_active else 0
                )
                binding.btnVisualGrid.setColorFilter(
                    if (state.isVisualGridOverlayVisible) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
                )
            }
        }
    }

    private fun setupFloatingZoomSlider() {
        var isProgrammaticChange = false

        // Synchronize View scale changes (pinch-zoom or programmatic) with Floating Slider
        binding.cognitiveCanvasView.onScaleChangedListener = { scale ->
            val percent = (scale * 100).toInt()
            binding.tvZoomPercent.text = "$percent%"

            if (!isProgrammaticChange) {
                // scale ranges from 0.20f to 4.0f -> progress 0 to 380
                val progress = ((scale - 0.20f) * 100f).toInt().coerceIn(0, 380)
                binding.seekBarZoom.progress = progress
            }
        }

        binding.seekBarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isProgrammaticChange = true
                    val newScale = 0.20f + (progress / 100f)
                    val percent = (newScale * 100).toInt()
                    binding.tvZoomPercent.text = "$percent%"
                    binding.cognitiveCanvasView.setManualScale(newScale)
                    isProgrammaticChange = false
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnZoomIn.setOnClickListener {
            val currentScale = binding.cognitiveCanvasView.viewport.scale
            val targetScale = (currentScale + 0.25f).coerceAtMost(4.0f)
            binding.cognitiveCanvasView.setManualScale(targetScale)
            val percent = (targetScale * 100).toInt()
            binding.tvZoomPercent.text = "$percent%"
            val progress = ((targetScale - 0.20f) * 100f).toInt().coerceIn(0, 380)
            binding.seekBarZoom.progress = progress
        }

        binding.btnZoomOut.setOnClickListener {
            val currentScale = binding.cognitiveCanvasView.viewport.scale
            val targetScale = (currentScale - 0.25f).coerceAtLeast(0.20f)
            binding.cognitiveCanvasView.setManualScale(targetScale)
            val percent = (targetScale * 100).toInt()
            binding.tvZoomPercent.text = "$percent%"
            val progress = ((targetScale - 0.20f) * 100f).toInt().coerceIn(0, 380)
            binding.seekBarZoom.progress = progress
        }
    }

    private fun promptLinkRelationshipType(source: CanvasNode, target: CanvasNode) {
        val linkOptions = arrayOf(
            "📁↔📁 Parent to Parent (Workspace peer association)",
            "📁↳📄 Parent to Child (Folder hierarchy membership)",
            "🔗 Custom Spatial Link (Freeform association)"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Link '${source.fileNode.name}' ➔ '${target.fileNode.name}'")
            .setItems(linkOptions) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.linkNodes(
                            sourceNode = source,
                            targetNode = target,
                            relationType = CanvasRelationType.PARENT_TO_PARENT,
                            label = "Parent-to-Parent"
                        )
                        binding.cognitiveCanvasView.invalidate()
                        Toast.makeText(this, "Linked as Parent-to-Parent peer", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        viewModel.linkNodes(
                            sourceNode = source,
                            targetNode = target,
                            relationType = CanvasRelationType.PARENT_TO_CHILD,
                            label = "Parent-to-Child"
                        )
                        binding.cognitiveCanvasView.invalidate()
                        Toast.makeText(this, "Linked as Parent-to-Child hierarchy", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        viewModel.linkNodes(
                            sourceNode = source,
                            targetNode = target,
                            relationType = CanvasRelationType.USER_LINK,
                            label = "User Link"
                        )
                        binding.cognitiveCanvasView.invalidate()
                        Toast.makeText(this, "Linked files", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportCanvasAsImage() {
        try {
            Toast.makeText(this, "Generating high-res canvas image...", Toast.LENGTH_SHORT).show()
            val bitmap = binding.cognitiveCanvasView.exportLayoutAsBitmap()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CognitiveCanvas")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val exportFile = File(exportDir, "Canvas_Layout_${timeStamp}.png")
            FileOutputStream(exportFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.flush()
            }

            // Share / view prompt
            val uri = try {
                FileProvider.getUriForFile(this, "${packageName}.provider", exportFile)
            } catch (ex: Exception) {
                Uri.fromFile(exportFile)
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Canvas Layout Exported")
                .setMessage("Saved image to:\n${exportFile.absolutePath}")
                .setPositiveButton("Share") { _, _ ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Canvas Layout"))
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
                if (::previewPanelController.isInitialized && previewPanelController.isShowing() &&
                    previewPanelController.getCurrentNode()?.id == node.id) {
                    previewPanelController.updateThemeColor(selectedColor)
                }
                val colorName = colorOptions[which]
                Toast.makeText(this, "Theme color updated: $colorName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFileOrDirectory(node: CanvasNode) {
        if (::previewPanelController.isInitialized) {
            previewPanelController.hidePreview()
        }
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::previewPanelController.isInitialized && previewPanelController.isShowing()) {
            previewPanelController.hidePreview()
            binding.cognitiveCanvasView.clearSelection()
            return
        }
        super.onBackPressed()
    }
}
