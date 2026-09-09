package com.polymath.fs.ui.canvas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
                if (isEnabled) "Snap to Grid Enabled" else "Snap to Grid Disabled",
                Toast.LENGTH_SHORT
            ).show()
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
                "💡 Long-press any file for Quick Actions (Open, Rename, Move, Delete)"
            }
        }

        // Reset camera / center
        binding.btnResetView.setOnClickListener {
            binding.cognitiveCanvasView.resetViewAnimated()
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
                    1 -> promptRenameFile(node)
                    2 -> promptMoveFile(node)
                    3 -> togglePinNode(node)
                    4 -> confirmDeleteFile(node)
                }
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
