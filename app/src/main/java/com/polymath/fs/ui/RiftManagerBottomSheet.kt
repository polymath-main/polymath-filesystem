package com.polymath.fs.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.polymath.fs.R
import java.io.File

class RiftManagerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private var riftFiles: List<File> = emptyList()
    var onRiftCast: ((File) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_rift_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.riftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Find all .rift files in common directories for now, or just the current dir
        // We will pass the current dir from the fragment
        recyclerView.adapter = RiftAdapter(riftFiles) { file ->
            onRiftCast?.invoke(file)
            dismiss()
        }
    }
    
    fun setRiftFiles(files: List<File>) {
        this.riftFiles = files
    }

    private inner class RiftAdapter(private val rifts: List<File>, private val onCast: (File) -> Unit) : RecyclerView.Adapter<RiftAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.riftName)
            val path: TextView = view.findViewById(R.id.riftPath)
            val btnCast: Button = view.findViewById(R.id.btnCast)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rift_script, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = rifts[position]
            holder.name.text = file.nameWithoutExtension
            holder.path.text = file.absolutePath
            holder.btnCast.setOnClickListener { onCast(file) }
        }

        override fun getItemCount() = rifts.size
    }
}
