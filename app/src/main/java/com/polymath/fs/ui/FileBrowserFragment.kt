package com.polymath.fs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.polymath.fs.R
import com.polymath.fs.databinding.FragmentFileBrowserBinding
import com.polymath.fs.viewmodels.FileSystemViewModel
import kotlinx.coroutines.launch

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FileSystemViewModel by activityViewModels {
        FileSystemViewModel.provideFactory(requireActivity().application as com.polymath.fs.PolymathApp)
    }
    private lateinit var adapter: FileListAdapter
    private lateinit var recentAdapter: FileListAdapter
    private var cabMode: androidx.appcompat.view.ActionMode? = null

    private val cabCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: androidx.appcompat.view.ActionMode, menu: android.view.Menu): Boolean {
            mode.menuInflater.inflate(com.polymath.fs.R.menu.menu_file_context, menu)
            menu.findItem(com.polymath.fs.R.id.action_info)?.isVisible = false
            menu.findItem(com.polymath.fs.R.id.action_rename)?.isVisible = false
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: android.view.Menu): Boolean = false

        override fun onActionItemClicked(mode: androidx.appcompat.view.ActionMode, item: android.view.MenuItem): Boolean {
            val paths = adapter.selectedItems.toList()
            when (item.itemId) {
                com.polymath.fs.R.id.action_bulk_script -> {
                    val sheet = BulkScriptExecutionBottomSheet()
                    sheet.setSelectedFiles(paths)
                    sheet.show(parentFragmentManager, "BulkScriptExecutionBottomSheet")
                    mode.finish()
                    return true
                }
                com.polymath.fs.R.id.action_delete -> {
                    confirmDelete(paths) {
                        mode.finish()
                    }
                    return true
                }
                com.polymath.fs.R.id.action_copy -> { viewModel.copyFiles(paths); mode.finish(); return true }
                com.polymath.fs.R.id.action_move -> { viewModel.cutFiles(paths); mode.finish(); return true }
                com.polymath.fs.R.id.action_permissions -> {
                    val layout = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(50, 40, 50, 10)
                    }
                    val modeInput = android.widget.EditText(requireContext()).apply { hint = "Mode (e.g. 755)" }
                    val ownerInput = android.widget.EditText(requireContext()).apply { hint = "Owner:Group" }
                    layout.addView(modeInput)
                    layout.addView(ownerInput)
                    
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Edit Permissions")
                        .setView(layout)
                        .setPositiveButton("OK") { _, _ ->
                            val pMode = modeInput.text.toString()
                            val pOwner = ownerInput.text.toString()
                            paths.forEach { p ->
                                if (pMode.isNotBlank()) viewModel.chmod(p, pMode)
                                if (pOwner.isNotBlank()) viewModel.chown(p, pOwner)
                            }
                            mode.finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            adapter.clearSelection()
            cabMode = null
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupToolbar()
        setupTabs()
        
        binding.copyPathButton.setOnClickListener {
            val currentPath = viewModel.uiState.value.activeTab?.currentPath ?: ""
            if (currentPath.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Copied Path", currentPath)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Path copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val activeTab = viewModel.uiState.value.activeTab
                if (activeTab != null && activeTab.currentPath != "/storage/emulated/0" && 
                    activeTab.currentPath != "/") {
                    viewModel.navigateUp()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        val onItemClick: (com.polymath.fs.models.FileNode) -> Unit = { fileNode ->
            if (fileNode.isDirectory) {
                viewModel.navigateTo(fileNode.path)
            } else {
                viewModel.addRecentFile(fileNode)
                val file = java.io.File(fileNode.path)
                val ext = file.extension.lowercase()
                if (ext == "txt" || ext == "md") {
                    val intent = android.content.Intent(requireContext(), com.polymath.fs.viewers.TextViewerActivity::class.java)
                    intent.putExtra("FILE_PATH", fileNode.path)
                    startActivity(intent)
                } else {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.setDataAndType(uri, mimeType)
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        val onMenuClick: (com.polymath.fs.models.FileNode, View) -> Unit = { fileNode, view ->
            val popup = android.widget.PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(com.polymath.fs.R.menu.menu_file_context, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    com.polymath.fs.R.id.action_info -> {
                        val bottomSheet = FileInfoBottomSheet()
                        bottomSheet.setFileNode(fileNode)
                        bottomSheet.show(parentFragmentManager, "FileInfoBottomSheet")
                        true
                    }
                    com.polymath.fs.R.id.action_delete -> {
                        confirmDelete(listOf(fileNode.path), fileNode.name)
                        true
                    }
                    com.polymath.fs.R.id.action_permissions -> {
                        val layout = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(50, 40, 50, 10)
                        }
                        val modeInput = android.widget.EditText(requireContext()).apply {
                            hint = "Mode (e.g. 755)"
                        }
                        val ownerInput = android.widget.EditText(requireContext()).apply {
                            hint = "Owner:Group (e.g. root:root)"
                        }
                        layout.addView(modeInput)
                        layout.addView(ownerInput)
                        
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Edit Permissions")
                            .setView(layout)
                            .setPositiveButton("OK") { _, _ ->
                                val mode = modeInput.text.toString()
                                val owner = ownerInput.text.toString()
                                if (mode.isNotBlank()) viewModel.chmod(fileNode.path, mode)
                                if (owner.isNotBlank()) viewModel.chown(fileNode.path, owner)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    com.polymath.fs.R.id.action_rename -> {
                        val input = android.widget.EditText(requireContext())
                        input.setText(fileNode.name)
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Rename")
                            .setView(input)
                            .setPositiveButton("OK") { _, _ ->
                                viewModel.renameFile(fileNode.path, input.text.toString())
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    com.polymath.fs.R.id.action_copy -> {
                        viewModel.copyFiles(listOf(fileNode.path))
                        true
                    }
                    com.polymath.fs.R.id.action_move -> {
                        viewModel.cutFiles(listOf(fileNode.path))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        
        adapter = FileListAdapter(
            onItemClick = onItemClick,
            onMenuClick = onMenuClick
        )
        adapter.onSelectionChange = { count ->
            if (count > 0) {
                if (cabMode == null) {
                    cabMode = (requireActivity() as androidx.appcompat.app.AppCompatActivity).startSupportActionMode(cabCallback)
                }
                cabMode?.title = "$count selected"
            } else {
                cabMode?.finish()
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        
        recentAdapter = FileListAdapter(
            onItemClick = onItemClick,
            onMenuClick = onMenuClick
        )
        binding.recentRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recentRecyclerView.adapter = recentAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val activeTab = state.activeTab
                    binding.pathText.text = activeTab?.currentPath ?: ""
                    binding.progressBar.visibility = if (activeTab?.isLoading == true) View.VISIBLE else View.GONE
                    
                    val pasteItem = binding.toolbar.menu.findItem(com.polymath.fs.R.id.action_paste)
                    pasteItem?.isVisible = state.clipboard != null

                    
                    if (activeTab != null && !activeTab.isLoading) {
                        val query = state.searchQuery.trim()
                        val displayedFiles = if (query.isEmpty()) {
                            activeTab.files
                        } else {
                            activeTab.files.filter { it.name.contains(query, ignoreCase = true) }
                        }
                        adapter.submitList(displayedFiles)
                        
                        if (displayedFiles.isEmpty()) {
                            binding.emptyText.text = if (query.isEmpty()) "Folder is empty" else "No matching files found"
                            binding.emptyText.visibility = View.VISIBLE
                        } else {
                            binding.emptyText.visibility = View.GONE
                        }
                        
                        binding.recentFilesPanel.visibility = if (activeTab.id == "general" && query.isEmpty()) View.VISIBLE else View.GONE
                    }
                    
                    adapter.setViewOptions(state.viewOptions)
                    recentAdapter.setViewOptions(state.viewOptions.copy(
                        layout = com.polymath.fs.models.ViewLayout.LIST, 
                        isVertical = false, 
                        boxSize = com.polymath.fs.models.BoxSize.MEDIUM, 
                        showDetails = false
                    ))
                    
                    when (state.viewOptions.layout) {
                        com.polymath.fs.models.ViewLayout.GRID -> {
                            val cols = state.viewOptions.columns.coerceAtLeast(2)
                            val lm = binding.recyclerView.layoutManager
                            if (lm !is androidx.recyclerview.widget.GridLayoutManager || lm.spanCount != cols) {
                                binding.recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), cols)
                                binding.recyclerView.adapter = adapter
                            }
                        }
                        com.polymath.fs.models.ViewLayout.HORIZONTAL -> {
                            val lm = binding.recyclerView.layoutManager
                            if (lm !is androidx.recyclerview.widget.LinearLayoutManager || lm is androidx.recyclerview.widget.GridLayoutManager || lm.orientation != androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL) {
                                binding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                                binding.recyclerView.adapter = adapter
                            }
                        }
                        else -> {
                            val lm = binding.recyclerView.layoutManager
                            if (lm !is androidx.recyclerview.widget.LinearLayoutManager || lm is androidx.recyclerview.widget.GridLayoutManager || lm.orientation != androidx.recyclerview.widget.LinearLayoutManager.VERTICAL) {
                                binding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                                binding.recyclerView.adapter = adapter
                            }
                        }
                    }
                    
                    recentAdapter.submitList(state.recentFiles)
                    
                    activeTab?.error?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }

                    if (binding.tabLayout.tabCount != state.tabs.size) {
                        binding.tabLayout.removeAllTabs()
                        state.tabs.forEach { tabState ->
                            val tabName = if (tabState.id == "general") "General" else if (tabState.currentPath == "/") "/" else tabState.currentPath.substringAfterLast('/')
                            val display = if (tabName.isEmpty()) "Root" else tabName
                            val tab = binding.tabLayout.newTab().setText(display).setTag(tabState.id)
                            binding.tabLayout.addTab(tab, false)
                        }
                    } else {
                        state.tabs.forEachIndexed { index, tabState ->
                            val tab = binding.tabLayout.getTabAt(index)
                            val tabName = if (tabState.id == "general") "General" else if (tabState.currentPath == "/") "/" else tabState.currentPath.substringAfterLast('/')
                            val display = if (tabName.isEmpty()) "Root" else tabName
                            if (tab?.text != display) {
                                tab?.text = display
                            }
                        }
                    }
                    
                    val activeIndex = state.tabs.indexOfFirst { it.id == state.activeTabId }
                    if (activeIndex >= 0 && binding.tabLayout.selectedTabPosition != activeIndex) {
                        binding.tabLayout.getTabAt(activeIndex)?.select()
                    }
                }
            }
        }
    }
    
    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val tabId = tab?.tag as? String
                if (tabId != null && tabId != viewModel.uiState.value.activeTabId) {
                    viewModel.switchTab(tabId)
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Polymath Files"
        binding.toolbar.setNavigationOnClickListener {
            viewModel.navigateUp()
        }
        binding.toolbar.inflateMenu(com.polymath.fs.R.menu.menu_browser)

        val searchItem = binding.toolbar.menu.findItem(com.polymath.fs.R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
        searchView?.queryHint = getString(com.polymath.fs.R.string.search_hint)
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
        searchItem?.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                viewModel.setSearchQuery("")
                return true
            }
        })

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.polymath.fs.R.id.action_view_mode -> {
                    com.polymath.fs.ui.ViewModeDialog.show(requireContext(), viewModel.uiState.value.viewOptions) { newOptions ->
                        viewModel.setViewOptions(newOptions)
                    }
                    true
                }
                com.polymath.fs.R.id.action_sort -> {
                    showSortingDialog()
                    true
                }
                com.polymath.fs.R.id.action_root -> {
                    viewModel.navigateTo("/")
                    true
                }
                com.polymath.fs.R.id.action_terminal -> {
                    startActivity(android.content.Intent(requireContext(), TerminalActivity::class.java))
                    true
                }
                com.polymath.fs.R.id.action_script_manager -> {
                    startActivity(android.content.Intent(requireContext(), ScriptManagerActivity::class.java))
                    true
                }
                com.polymath.fs.R.id.action_new_tab -> {
                    viewModel.newTab()
                    true
                }
                com.polymath.fs.R.id.action_close_tab -> {
                    val activeTabId = viewModel.uiState.value.activeTabId
                    if (activeTabId.isNotEmpty()) {
                        viewModel.closeTab(activeTabId)
                    }
                    true
                }
                com.polymath.fs.R.id.action_paste -> {
                    viewModel.pasteFiles()
                    true
                }
                com.polymath.fs.R.id.action_settings -> {
                    startActivity(android.content.Intent(requireContext(), SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun showSortingDialog() {
        val currentConfig = viewModel.uiState.value.sortConfig
        val options = arrayOf(
            "Name (A to Z)",
            "Name (Z to A)",
            "Date Modified (Newest first)",
            "Date Modified (Oldest first)",
            "Size (Largest first)",
            "Size (Smallest first)"
        )

        val selectedIndex = when (currentConfig.option) {
            com.polymath.fs.models.SortOption.NAME -> {
                if (currentConfig.direction == com.polymath.fs.models.SortDirection.ASCENDING) 0 else 1
            }
            com.polymath.fs.models.SortOption.TIME -> {
                if (currentConfig.direction == com.polymath.fs.models.SortDirection.DESCENDING) 2 else 3
            }
            com.polymath.fs.models.SortOption.SIZE -> {
                if (currentConfig.direction == com.polymath.fs.models.SortDirection.DESCENDING) 4 else 5
            }
            else -> 0
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                when (which) {
                    0 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.NAME, com.polymath.fs.models.SortDirection.ASCENDING)
                    1 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.NAME, com.polymath.fs.models.SortDirection.DESCENDING)
                    2 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.TIME, com.polymath.fs.models.SortDirection.DESCENDING)
                    3 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.TIME, com.polymath.fs.models.SortDirection.ASCENDING)
                    4 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.SIZE, com.polymath.fs.models.SortDirection.DESCENDING)
                    5 -> viewModel.setSortConfig(com.polymath.fs.models.SortOption.SIZE, com.polymath.fs.models.SortDirection.ASCENDING)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDelete(paths: List<String>, singleFileName: String? = null, onDeleted: (() -> Unit)? = null) {
        if (paths.isEmpty()) return

        val message = if (paths.size == 1) {
            val name = singleFileName ?: paths[0].substringAfterLast('/')
            getString(R.string.confirm_delete_single, name)
        } else {
            getString(R.string.confirm_delete_multiple, paths.size)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteFiles(paths)
                onDeleted?.invoke()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
