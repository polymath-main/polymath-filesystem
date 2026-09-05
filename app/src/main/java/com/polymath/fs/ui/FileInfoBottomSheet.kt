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

        fileNode?.let {
            tvName.text = it.name
            tvPath.text = it.path
            tvSize.text = "${it.size} bytes"
            tvType.text = if (it.isDirectory) "Directory" else "File"
        }

        return view
    }

    fun setFileNode(node: FileNode) {
        this.fileNode = node
    }
}
