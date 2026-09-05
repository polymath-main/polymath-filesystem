package com.polymath.fs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.polymath.fs.databinding.FragmentFileBrowserBinding
import com.polymath.fs.viewmodels.FileSystemViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FileSystemViewModel by viewModels()
    private lateinit var adapter: FileListAdapter

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
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val activeTab = viewModel.uiState.value.activeTab
                if (activeTab != null && activeTab.currentPath != "/storage/emulated/0" && 
                    activeTab.currentPath != "/") {
                    viewModel.navigateUp()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = FileListAdapter(
            onItemClick = { fileNode ->
                if (fileNode.isDirectory) {
                    viewModel.navigateTo(fileNode.path)
                } else {
                    Toast.makeText(context, "Opened: ${fileNode.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onMenuClick = { fileNode, view ->
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
                            viewModel.deleteFiles(listOf(fileNode.path))
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
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val activeTab = state.activeTab
                    binding.pathText.text = activeTab?.currentPath ?: ""
                    binding.progressBar.visibility = if (activeTab?.isLoading == true) View.VISIBLE else View.GONE
                    
                    if (activeTab != null && !activeTab.isLoading) {
                        adapter.submitList(activeTab.files)
                        binding.emptyText.visibility = if (activeTab.files.isEmpty()) View.VISIBLE else View.GONE
                    }
                    
                    activeTab?.error?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }

                    if (binding.tabLayout.tabCount != state.tabs.size) {
                        binding.tabLayout.removeAllTabs()
                        state.tabs.forEach { tabState ->
                            val tabName = if (tabState.currentPath == "/") "/" else tabState.currentPath.substringAfterLast('/')
                            val display = if (tabName.isEmpty()) "Root" else tabName
                            val tab = binding.tabLayout.newTab().setText(display).setTag(tabState.id)
                            binding.tabLayout.addTab(tab, false)
                        }
                    } else {
                        state.tabs.forEachIndexed { index, tabState ->
                            val tab = binding.tabLayout.getTabAt(index)
                            val tabName = if (tabState.currentPath == "/") "/" else tabState.currentPath.substringAfterLast('/')
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
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.polymath.fs.R.id.action_root -> {
                    viewModel.navigateTo("/")
                    true
                }
                com.polymath.fs.R.id.action_terminal -> {
                    startActivity(android.content.Intent(requireContext(), TerminalActivity::class.java))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
