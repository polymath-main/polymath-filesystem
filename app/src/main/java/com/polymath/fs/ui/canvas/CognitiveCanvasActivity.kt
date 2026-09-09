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
import android.graphics.Rect
import coil.load
import coil.size.Scale
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.fs.R
import com.polymath.fs.core.SystemBarHelper
import com.polymath.fs.core.ThemeManager
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity
import com.polymath.fs.databinding.ActivityCognitiveCanvasBinding
import com.polymath.fs.databinding.LayoutDialogCanvasPresetsBinding
import com.polymath.fs.databinding.LayoutDialogMlSuggestionsBinding
import com.polymath.fs.databinding.LayoutDialogWorkspaceSnapshotsBinding
import com.polymath.fs.domain.canvas.models.CanvasAction
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.domain.canvas.models.extension
import com.polymath.fs.domain.canvas.models.CanvasRelationType
import com.polymath.fs.domain.canvas.models.WorkspaceSnapshot
import com.polymath.fs.ui.canvas.adapters.CanvasPresetAdapter
import com.polymath.fs.ui.canvas.adapters.MLSuggestionClusterAdapter
import com.polymath.fs.ui.canvas.adapters.WorkspaceSnapshotAdapter
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

        // Ensure top panel contents render below status bar using WindowInsetsCompat while maintaining layout visual transparency
        val baseHeaderMarginTop = (binding.headerCard.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
            ?.takeIf { it > 0 } ?: (12 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (binding.headerCard.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = statusBarInsets.top + baseHeaderMarginTop
                binding.headerCard.layoutParams = lp
            }

            // Adjust navigation bar insets for preview sheet
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.previewPanel.root.setPadding(
                binding.previewPanel.root.paddingLeft,
                binding.previewPanel.root.paddingTop,
                binding.previewPanel.root.paddingRight,
                navBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

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

        // Activity Heatmap toggle (Recency & Hotspot halos)
        binding.btnHeatmap.setOnClickListener {
            val isEnabled = binding.cognitiveCanvasView.toggleHeatmap()
            binding.btnHeatmap.setBackgroundResource(
                if (isEnabled) R.drawable.bg_canvas_tool_pill_hot else R.drawable.bg_canvas_tool_pill
            )
            binding.ivHeatmapIcon.setColorFilter(
                if (isEnabled) Color.parseColor("#FF2A6D") else Color.parseColor("#94A3B8")
            )
            binding.tvHeatmapText.setTextColor(
                if (isEnabled) Color.parseColor("#FF2A6D") else Color.parseColor("#E2E8F0")
            )
            Toast.makeText(
                this,
                if (isEnabled) "Activity Heatmap Active: Thermal Halos & Activity Indicators" else "Heatmap Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ML Auto-Grouping suggestions
        binding.btnMLSuggest.setOnClickListener {
            showMLSuggestionsDialog()
        }

        // Spatial Canvas Presets (Project Flow, Chronological, Resource Clusters, Grid Matrix)
        binding.btnPresets.setOnClickListener {
            showCanvasPresetsDialog()
        }

        // Workspace Snapshots (Timestamped Layout Reversion)
        binding.btnSnapshots.setOnClickListener {
            showWorkspaceSnapshotsDialog()
        }

        // Snap to grid toggle
        binding.btnSnapToGrid.setOnClickListener {
            binding.cognitiveCanvasView.isSnapToGridEnabled = !binding.cognitiveCanvasView.isSnapToGridEnabled
            val isEnabled = binding.cognitiveCanvasView.isSnapToGridEnabled
            binding.btnSnapToGrid.setBackgroundResource(
                if (isEnabled) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_canvas_tool_pill
            )
            binding.ivSnapIcon.setColorFilter(
                if (isEnabled) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            binding.tvSnapText.setTextColor(
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
                if (isVisible) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_canvas_tool_pill
            )
            binding.ivGridLinesIcon.setColorFilter(
                if (isVisible) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            binding.tvGridLinesText.setTextColor(
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
                if (isLinkActive) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_canvas_tool_pill
            )
            binding.ivLinkIcon.setColorFilter(
                if (isLinkActive) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            binding.tvLinkText.setTextColor(
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

        // Long-press on node -> Context Menu Quick Actions & File Preview Peek
        binding.cognitiveCanvasView.onNodeLongClickListener = { node ->
            showFilePreviewPeek(node)
            showNodeContextMenu(node)
        }

        // Drag and Drop Quick Actions Overlay
        binding.cognitiveCanvasView.onNodeDragStartedListener = { _, _, _ ->
            binding.root.findViewById<View>(R.id.quickActionsOverlay)?.visibility = View.VISIBLE
        }
        
        binding.cognitiveCanvasView.onNodeDragMovedListener = { _, x, y ->
            val moveZone = binding.root.findViewById<View>(R.id.dropZoneMove)
            val copyZone = binding.root.findViewById<View>(R.id.dropZoneCopy)
            val deleteZone = binding.root.findViewById<View>(R.id.dropZoneDelete)
            
            val moveRect = Rect().apply { moveZone?.getGlobalVisibleRect(this) }
            val copyRect = Rect().apply { copyZone?.getGlobalVisibleRect(this) }
            val deleteRect = Rect().apply { deleteZone?.getGlobalVisibleRect(this) }
            
            moveZone?.setBackgroundResource(if (moveRect.contains(x.toInt(), y.toInt())) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_coachmark_card)
            copyZone?.setBackgroundResource(if (copyRect.contains(x.toInt(), y.toInt())) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_coachmark_card)
            deleteZone?.setBackgroundResource(if (deleteRect.contains(x.toInt(), y.toInt())) R.drawable.bg_canvas_tool_pill_hot else R.drawable.bg_coachmark_card)
        }

        binding.cognitiveCanvasView.onNodeDragEndedListener = { node, x, y ->
            binding.root.findViewById<View>(R.id.quickActionsOverlay)?.visibility = View.GONE
            
            val moveZone = binding.root.findViewById<View>(R.id.dropZoneMove)
            val copyZone = binding.root.findViewById<View>(R.id.dropZoneCopy)
            val deleteZone = binding.root.findViewById<View>(R.id.dropZoneDelete)
            
            val moveRect = Rect().apply { moveZone?.getGlobalVisibleRect(this) }
            val copyRect = Rect().apply { copyZone?.getGlobalVisibleRect(this) }
            val deleteRect = Rect().apply { deleteZone?.getGlobalVisibleRect(this) }
            
            if (moveRect.contains(x.toInt(), y.toInt())) {
                promptMoveFile(node)
            } else if (copyRect.contains(x.toInt(), y.toInt())) {
                promptCopyFile(node)
            } else if (deleteRect.contains(x.toInt(), y.toInt())) {
                confirmDeleteFile(node)
            }
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
            binding.root.findViewById<View>(R.id.filePreviewPeekOverlay)?.visibility = View.GONE
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
                    if (state.isVisualGridOverlayVisible) R.drawable.bg_canvas_tool_pill_active else R.drawable.bg_canvas_tool_pill
                )
                binding.ivGridLinesIcon.setColorFilter(
                    if (state.isVisualGridOverlayVisible) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
                )
                binding.tvGridLinesText.setTextColor(
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
        val bottomSheet = NodeContextMenuBottomSheet.newInstance(node)
        bottomSheet.onOpenClick = { openFileOrDirectory(node) }
        bottomSheet.onColorClick = { showThemeColorPicker(node) }
        bottomSheet.onRenameClick = { promptRenameFile(node) }
        bottomSheet.onMoveClick = { promptMoveFile(node) }
        bottomSheet.onPinClick = { togglePinNode(node) }
        bottomSheet.onDeleteClick = { confirmDeleteFile(node) }
        bottomSheet.show(supportFragmentManager, "NodeContextMenuBottomSheet")
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

    private fun promptCopyFile(node: CanvasNode) {
        val editText = EditText(this).apply {
            hint = "Destination directory path"
            setText(File(node.fileNode.path).parent ?: currentDirectoryPath)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Copy '${node.fileNode.name}'")
            .setView(editText)
            .setPositiveButton("Copy") { _, _ ->
                val destPath = editText.text.toString().trim()
                if (destPath.isNotEmpty()) {
                    viewModel.copyFile(node, destPath) { success, msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFilePreviewPeek(node: CanvasNode) {
        val file = File(node.fileNode.path)
        val ext = node.fileNode.extension.lowercase(Locale.getDefault())

        val peekView = binding.root.findViewById<View>(R.id.filePreviewPeekOverlay)
        val ivPeek = binding.root.findViewById<android.widget.ImageView>(R.id.ivOverlayThumbnail)
        val tvSnippet = binding.root.findViewById<android.widget.TextView>(R.id.tvOverlaySnippet)

        peekView?.visibility = View.VISIBLE
        
        peekView?.translationX = (binding.root.width / 2f) - (160 * resources.displayMetrics.density / 2f)
        peekView?.translationY = (binding.root.height / 2f) - (160 * resources.displayMetrics.density / 2f)
        
        val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "svg")
        val isTextOrCode = ext in listOf("kt", "java", "py", "js", "html", "css", "json", "xml", "txt", "md", "c", "cpp", "h", "sh", "sql")

        tvSnippet?.visibility = View.GONE
        ivPeek?.visibility = View.VISIBLE

        if (isImage && file.exists()) {
            ivPeek?.load(file) { crossfade(true) }
        } else if (isTextOrCode && file.exists()) {
            ivPeek?.visibility = View.GONE
            tvSnippet?.visibility = View.VISIBLE
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val snippet = try {
                    file.bufferedReader().useLines { seq -> seq.take(10).joinToString("\n") }
                } catch(e: Exception) { "" }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    tvSnippet?.text = snippet
                }
            }
        } else {
            ivPeek?.load(R.drawable.ic_file_default)
        }
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

    private fun showMLSuggestionsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = LayoutDialogMlSuggestionsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val adapter = MLSuggestionClusterAdapter { cluster ->
            viewModel.applyMLGroupingSuggestion(cluster) {
                binding.cognitiveCanvasView.invalidate()
                Toast.makeText(this, "Auto-grouped: ${cluster.title}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialogBinding.rvMLClusters.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvMLClusters.adapter = adapter
        dialogBinding.btnCloseMLDialog.setOnClickListener { dialog.dismiss() }

        dialogBinding.pbMLLoading.visibility = View.VISIBLE
        dialogBinding.rvMLClusters.visibility = View.GONE
        dialogBinding.tvMLEmpty.visibility = View.GONE

        viewModel.computeMLGroupingSuggestions { clusters ->
            dialogBinding.pbMLLoading.visibility = View.GONE
            if (clusters.isEmpty()) {
                dialogBinding.tvMLEmpty.visibility = View.VISIBLE
                dialogBinding.rvMLClusters.visibility = View.GONE
            } else {
                dialogBinding.tvMLEmpty.visibility = View.GONE
                dialogBinding.rvMLClusters.visibility = View.VISIBLE
                adapter.submitList(clusters)
            }
        }

        dialog.show()
    }

    private fun showCanvasPresetsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = LayoutDialogCanvasPresetsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val adapter = CanvasPresetAdapter { preset ->
            viewModel.applyCanvasPreset(preset) {
                binding.cognitiveCanvasView.invalidate()
                Toast.makeText(this, "Applied preset: ${preset.name}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialogBinding.rvPresetsList.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPresetsList.adapter = adapter
        dialogBinding.btnClosePresetsDialog.setOnClickListener { dialog.dismiss() }

        viewModel.getCanvasPresets { presets ->
            adapter.submitList(presets)
        }

        dialogBinding.btnSaveCurrentPreset.setOnClickListener {
            val editText = EditText(this).apply {
                hint = "Preset Name (e.g., Project Pipeline)"
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Save Canvas Preset")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val name = editText.text.toString().trim()
                    if (name.isNotEmpty()) {
                        viewModel.saveCurrentLayoutAsPreset(
                            name = name,
                            description = "Custom layout arrangement for $currentDirectoryPath"
                        ) { success ->
                            if (success) {
                                Toast.makeText(this, "Preset '$name' saved!", Toast.LENGTH_SHORT).show()
                                viewModel.getCanvasPresets { presets ->
                                    adapter.submitList(presets)
                                }
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }

    private fun showWorkspaceSnapshotsDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = LayoutDialogWorkspaceSnapshotsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        lateinit var adapter: WorkspaceSnapshotAdapter
        val refreshSnapshots = {
            viewModel.getWorkspaceSnapshots { snapshots ->
                if (snapshots.isEmpty()) {
                    dialogBinding.tvSnapshotsEmpty.visibility = View.VISIBLE
                    dialogBinding.rvSnapshotsList.visibility = View.GONE
                } else {
                    dialogBinding.tvSnapshotsEmpty.visibility = View.GONE
                    dialogBinding.rvSnapshotsList.visibility = View.VISIBLE
                    adapter.submitList(snapshots)
                }
            }
        }

        adapter = WorkspaceSnapshotAdapter(
            onRestoreSnapshot = { snapshot ->
                viewModel.restoreWorkspaceSnapshot(snapshot) {
                    binding.cognitiveCanvasView.invalidate()
                    Toast.makeText(this, "Restored snapshot: ${snapshot.label}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            },
            onDeleteSnapshot = { entity ->
                viewModel.deleteWorkspaceSnapshot(entity.id) {
                    Toast.makeText(this, "Snapshot deleted", Toast.LENGTH_SHORT).show()
                    refreshSnapshots()
                }
            }
        )
        dialogBinding.rvSnapshotsList.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvSnapshotsList.adapter = adapter
        dialogBinding.btnCloseSnapshotsDialog.setOnClickListener { dialog.dismiss() }

        refreshSnapshots()

        dialogBinding.btnCaptureSnapshot.setOnClickListener {
            val labelText = dialogBinding.etSnapshotLabel.text.toString().trim().ifEmpty {
                "Snapshot " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
            }
            viewModel.captureWorkspaceSnapshot(labelText) {
                dialogBinding.etSnapshotLabel.setText("")
                hideKeyboard(dialogBinding.etSnapshotLabel)
                Toast.makeText(this, "Snapshot '$labelText' captured!", Toast.LENGTH_SHORT).show()
                refreshSnapshots()
            }
        }

        dialog.show()
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
