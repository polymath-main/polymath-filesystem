package com.polymath.fs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.polymath.fs.R
import com.polymath.fs.models.FileNode

class FileInfoBottomSheet : BottomSheetDialogFragment() {

    private var fileNode: FileNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve arguments
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_file_info, container, false)
        val tvName = view.findViewById<TextView>(R.id.tvName)
        val tvPath = view.findViewById<TextView>(R.id.tvPath)
        val tvSize = view.findViewById<TextView>(R.id.tvSize)
        val tvType = view.findViewById<TextView>(R.id.tvType)

        val btnCopyPath = view.findViewById<android.widget.Button>(R.id.btnCopyPath)

        fileNode?.let { node ->
            tvName.text = node.name
            tvPath.text = node.path
            tvSize.text = "${node.size} bytes"
            tvType.text = if (node.isDirectory) "Directory" else "File"
            
            btnCopyPath.setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Copied Path", node.path)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(requireContext(), "Path copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    fun setFileNode(node: FileNode) {
        this.fileNode = node
    }
}
