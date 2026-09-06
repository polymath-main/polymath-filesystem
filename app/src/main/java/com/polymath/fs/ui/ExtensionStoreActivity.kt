package com.polymath.fs.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.ExtensionManager
import com.polymath.fs.core.ExtensionMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL

class ExtensionStoreActivity : BaseDynamicActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ExtensionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extension_store)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ExtensionAdapter(this) { metadata -> installExtension(metadata) }
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchExtensions() }
        
        fetchExtensions()
    }

    private fun fetchExtensions() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Production-grade fetch from remote registry
                val jsonStr = URL("https://raw.githubusercontent.com/polymath-main/polymath-filesystem/main/extensions.json").readText()
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<ExtensionMetadata>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ExtensionMetadata(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            version = obj.getString("version"),
                            author = obj.getString("author"),
                            description = obj.getString("description"),
                            scriptUrl = obj.getString("scriptUrl"),
                            manifestUrl = obj.getString("manifestUrl")
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    adapter.submitList(list)
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionStoreActivity, "Failed to connect to marketplace", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun installExtension(metadata: ExtensionMetadata) {
        Toast.makeText(this, "Downloading ${metadata.name}...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scriptContent = URL(metadata.scriptUrl).readText()
                val manifestContent = URL(metadata.manifestUrl).readText()

                val extDir = File(getDir("app_extensions", Context.MODE_PRIVATE), metadata.id)
                if (!extDir.exists()) extDir.mkdirs()

                File(extDir, "main.js").writeText(scriptContent)
                File(extDir, "manifest.json").writeText(manifestContent)

                // Register hooks if they exist in the manifest
                val manifestObj = org.json.JSONObject(manifestContent)
                if (manifestObj.has("contextHooks")) {
                    val hooksArray = manifestObj.getJSONArray("contextHooks")
                    for (i in 0 until hooksArray.length()) {
                        val hookObj = hooksArray.getJSONObject(i)
                        ExtensionManager.registerContextHook(
                            id = metadata.id + "_" + i,
                            displayName = hookObj.getString("displayName"),
                            filterRegex = hookObj.getString("filterRegex"),
                            jsCallbackId = metadata.id
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionStoreActivity, "Successfully installed ${metadata.name}!", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExtensionStoreActivity, "Failed to install ${metadata.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

class ExtensionAdapter(
    private val context: Context,
    private val onInstallClicked: (ExtensionMetadata) -> Unit
) : RecyclerView.Adapter<ExtensionAdapter.ViewHolder>() {

    private var items = listOf<ExtensionMetadata>()

    fun submitList(list: List<ExtensionMetadata>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtAuthor: TextView = view.findViewById(R.id.txtAuthor)
        val txtDescription: TextView = view.findViewById(R.id.txtDescription)
        val btnInstall: Button = view.findViewById(R.id.btnInstall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_extension_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.name
        holder.txtAuthor.text = "by ${item.author} v${item.version}"
        holder.txtDescription.text = item.description

        val extDir = File(context.getDir("app_extensions", Context.MODE_PRIVATE), item.id)
        if (extDir.exists() && File(extDir, "main.js").exists()) {
            holder.btnInstall.text = "Installed"
            holder.btnInstall.isEnabled = false
        } else {
            holder.btnInstall.text = "Install"
            holder.btnInstall.isEnabled = true
            holder.btnInstall.setOnClickListener {
                onInstallClicked(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
