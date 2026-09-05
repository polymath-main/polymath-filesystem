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
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.uiState.value.currentPath != "/storage/emulated/0" && 
                    viewModel.uiState.value.currentPath != "/") {
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
                    binding.pathText.text = state.currentPath
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    if (!state.isLoading) {
                        adapter.submitList(state.files)
                        binding.emptyText.visibility = if (state.files.isEmpty()) View.VISIBLE else View.GONE
                    }
                    
                    state.error?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
                com.polymath.fs.R.id.action_paste -> {
                    viewModel.pasteFiles()
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
