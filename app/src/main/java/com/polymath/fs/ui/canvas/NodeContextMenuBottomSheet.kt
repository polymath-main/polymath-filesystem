package com.polymath.fs.ui.canvas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.polymath.fs.R
import com.polymath.fs.domain.canvas.models.CanvasNode
import java.io.File

class NodeContextMenuBottomSheet : BottomSheetDialogFragment() {

    private var node: CanvasNode? = null
    var onOpenClick: (() -> Unit)? = null
    var onColorClick: (() -> Unit)? = null
    var onRenameClick: (() -> Unit)? = null
    var onMoveClick: (() -> Unit)? = null
    var onPinClick: (() -> Unit)? = null
    var onDeleteClick: (() -> Unit)? = null

    companion object {
        fun newInstance(node: CanvasNode): NodeContextMenuBottomSheet {
            return NodeContextMenuBottomSheet().apply {
                this.node = node
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_node_context_menu, container, false)
        
        val tvOpen = view.findViewById<TextView>(R.id.tvOpen)
        val tvColor = view.findViewById<TextView>(R.id.tvColor)
        val tvRename = view.findViewById<TextView>(R.id.tvRename)
        val tvMove = view.findViewById<TextView>(R.id.tvMove)
        val tvPin = view.findViewById<TextView>(R.id.tvPin)
        val tvDelete = view.findViewById<TextView>(R.id.tvDelete)

        node?.let { n ->
            val file = File(n.fileNode.path)
            val isDir = file.isDirectory
            tvOpen.text = if (isDir) "Open Directory" else "Open File"
            tvPin.text = if (n.isPinned) "Unpin Position" else "Pin Position"
        }

        tvOpen.setOnClickListener {
            onOpenClick?.invoke()
            dismiss()
        }
        tvColor.setOnClickListener {
            onColorClick?.invoke()
            dismiss()
        }
        tvRename.setOnClickListener {
            onRenameClick?.invoke()
            dismiss()
        }
        tvMove.setOnClickListener {
            onMoveClick?.invoke()
            dismiss()
        }
        tvPin.setOnClickListener {
            onPinClick?.invoke()
            dismiss()
        }
        tvDelete.setOnClickListener {
            onDeleteClick?.invoke()
            dismiss()
        }

        return view
    }
}
