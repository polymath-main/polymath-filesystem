package com.polymath.fs.ui.synapse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.SynapseEngine
import com.polymath.fs.databinding.ActivitySynapseBinding
import com.polymath.fs.databinding.ItemSynapseFileBinding
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SynapseActivity : BaseDynamicActivity() {

    private lateinit var binding: ActivitySynapseBinding
    private lateinit var synapseEngine: SynapseEngine
    private val remoteFilesAdapter = RemoteFilesAdapter()

    private var currentRemotePath: String = "/storage/emulated/0"
    private var activePeerIp: String = "192.168.1.50"
    private var activePeerPort: Int = 8888
    private var browseJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySynapseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as PolymathApp
        synapseEngine = app.synapseEngine

        applyEdgeToEdgeInsets()
        setupTopBar()
        setupNodeServerCard()
        setupRemoteConnectCard()
        setupRemoteDirectoryBrowser()
        setupClippingHub()
        observeSynapseEngineState()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootSynapseLayout) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
            binding.synapseTopBar.setPadding(
                binding.synapseTopBar.paddingLeft,
                systemBars.top + 12,
                binding.synapseTopBar.paddingRight,
                12
            )
            binding.synapseMainContainer.setPadding(
                0,
                0,
                0,
                systemBars.bottom
            )
            insets
        }
    }

    private fun setupTopBar() {
        binding.btnBackSynapse.setOnClickListener {
            finish()
        }
    }

    private fun setupNodeServerCard() {
        // Display local network IP
        val localIp = synapseEngine.getLocalIpAddress()
        binding.tvLocalIp.text = localIp
        binding.tvLocalPort.text = synapseEngine.port.toString()

        binding.btnCopyIp.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Synapse Local Node IP", localIp)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Local Node IP copied: $localIp", Toast.LENGTH_SHORT).show()
        }

        // Toggle Server Switch
        binding.switchServerToggle.isChecked = synapseEngine.isRunning
        updateServerStatusBadge(synapseEngine.isRunning)

        binding.switchServerToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                synapseEngine.startSynapse()
                updateServerStatusBadge(true)
                Toast.makeText(this, "Synapse Daemon running on port ${synapseEngine.port}", Toast.LENGTH_SHORT).show()
            } else {
                synapseEngine.stopSynapse()
                updateServerStatusBadge(false)
                Toast.makeText(this, "Synapse Daemon stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto-start server if not already running
        if (!synapseEngine.isRunning) {
            synapseEngine.startSynapse()
            binding.switchServerToggle.isChecked = true
            updateServerStatusBadge(true)
        }
    }

    private fun updateServerStatusBadge(isRunning: Boolean) {
        if (isRunning) {
            binding.tvServerStatusBadge.text = "RUNNING"
            binding.tvServerStatusBadge.setBackgroundResource(R.drawable.bg_badge_status_running)
            binding.tvServerStatusBadge.setTextColor(android.graphics.Color.parseColor("#22c55e"))
        } else {
            binding.tvServerStatusBadge.text = "STOPPED"
            binding.tvServerStatusBadge.setBackgroundResource(R.drawable.bg_badge_status_stopped)
            binding.tvServerStatusBadge.setTextColor(android.graphics.Color.parseColor("#f59e0b"))
        }
    }

    private fun setupRemoteConnectCard() {
        binding.btnConnectPeer.setOnClickListener {
            val ip = binding.etPeerIp.text?.toString()?.trim().orEmpty()
            val portStr = binding.etPeerPort.text?.toString()?.trim().orEmpty()
            val port = portStr.toIntOrNull() ?: 8888

            if (ip.isEmpty()) {
                binding.tilPeerIp.error = "Enter a valid peer IP"
                return@setOnClickListener
            }
            binding.tilPeerIp.error = null

            activePeerIp = ip
            activePeerPort = port
            currentRemotePath = "/storage/emulated/0"

            connectAndBrowseRemote(activePeerIp, currentRemotePath, activePeerPort)
        }
    }

    private fun setupRemoteDirectoryBrowser() {
        binding.rvRemoteFiles.layoutManager = LinearLayoutManager(this)
        binding.rvRemoteFiles.adapter = remoteFilesAdapter

        binding.tvRemoteCurrentPath.text = currentRemotePath

        binding.btnRemoteUp.setOnClickListener {
            if (currentRemotePath == "/" || currentRemotePath == "/storage/emulated/0" || currentRemotePath.isEmpty()) {
                Toast.makeText(this, "At top directory", Toast.LENGTH_SHORT).show()
            } else {
                val parentPath = if (currentRemotePath.contains("/")) {
                    val p = currentRemotePath.substringBeforeLast("/")
                    if (p.isEmpty()) "/" else p
                } else {
                    "/"
                }
                currentRemotePath = parentPath
                connectAndBrowseRemote(activePeerIp, currentRemotePath, activePeerPort)
            }
        }

        binding.btnRemoteRefresh.setOnClickListener {
            connectAndBrowseRemote(activePeerIp, currentRemotePath, activePeerPort)
        }

        remoteFilesAdapter.onFolderClick = { folder ->
            currentRemotePath = folder.path
            connectAndBrowseRemote(activePeerIp, currentRemotePath, activePeerPort)
        }

        remoteFilesAdapter.onDownloadClick = { fileNode, onComplete ->
            downloadRemoteFile(fileNode, onComplete)
        }
    }

    private fun connectAndBrowseRemote(ip: String, path: String, port: Int) {
        binding.progressPeerConnecting.visibility = View.VISIBLE
        binding.tvPeerConnectionStatus.text = "Querying peer $ip:$port..."
        binding.tvRemoteCurrentPath.text = path

        browseJob?.cancel()
        browseJob = lifecycleScope.launch {
            val files = synapseEngine.fetchRemoteDirectory(ip, path, port)
            binding.progressPeerConnecting.visibility = View.GONE

            if (files.isNotEmpty()) {
                binding.tvPeerConnectionStatus.text = "Connected · ${files.size} entries listed"
                binding.layoutRemoteEmpty.visibility = View.GONE
                binding.rvRemoteFiles.visibility = View.VISIBLE
                remoteFilesAdapter.submitList(files)
            } else {
                binding.tvPeerConnectionStatus.text = "Connected (0 items or unreachable)"
                binding.rvRemoteFiles.visibility = View.GONE
                binding.layoutRemoteEmpty.visibility = View.VISIBLE
                binding.tvRemoteEmptyMessage.text = "No files found at $path or peer connection timed out."
            }
        }
    }

    private fun downloadRemoteFile(fileNode: FileNode, onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Synapse"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val targetFile = File(downloadDir, fileNode.name)
            Toast.makeText(this@SynapseActivity, "Downloading ${fileNode.name}...", Toast.LENGTH_SHORT).show()

            val success = synapseEngine.downloadRemoteFile(
                ip = activePeerIp,
                remotePath = fileNode.path,
                destFile = targetFile,
                targetPort = activePeerPort
            )

            onComplete(success)

            if (success) {
                Snackbar.make(
                    binding.root,
                    "Saved to Downloads/Synapse/${fileNode.name}",
                    Snackbar.LENGTH_LONG
                ).setAction("Open") {
                    val intent = android.content.Intent(this@SynapseActivity, com.polymath.fs.viewers.EditorActivity::class.java).apply {
                        putExtra("path", targetFile.absolutePath)
                        putExtra("filePath", targetFile.absolutePath)
                    }
                    startActivity(intent)
                }.show()
            } else {
                Toast.makeText(
                    this@SynapseActivity,
                    "Download failed from $activePeerIp:${activePeerPort}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupClippingHub() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        binding.btnCopyReceivedClip.setOnClickListener {
            val clipText = binding.tvLatestClipContent.text.toString()
            if (clipText.isNotBlank() && clipText != "No clip received yet. Mesh nodes broadcasting clips will appear here.") {
                val clip = ClipData.newPlainText("Synapse Mesh Clip", clipText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPasteToBroadcast.setOnClickListener {
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteText = item?.text?.toString().orEmpty()
            if (pasteText.isNotBlank()) {
                binding.etBroadcastClipText.setText(pasteText)
            } else {
                Toast.makeText(this, "System clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBroadcastClip.setOnClickListener {
            val text = binding.etBroadcastClipText.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                binding.tilBroadcastClip.error = "Enter text to broadcast"
                return@setOnClickListener
            }
            binding.tilBroadcastClip.error = null

            val targetIp = binding.etPeerIp.text?.toString()?.trim().orEmpty()
            val targetPort = binding.etPeerPort.text?.toString()?.trim()?.toIntOrNull() ?: 8888

            if (targetIp.isEmpty()) {
                Toast.makeText(this, "Specify peer IP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tvClipBroadcastStatus.text = "Broadcasting to $targetIp:$targetPort..."

            lifecycleScope.launch {
                val ok = synapseEngine.sendClip(targetIp, text, targetPort)
                if (ok) {
                    binding.tvClipBroadcastStatus.text = "Successfully transmitted to mesh peer $targetIp:$targetPort"
                    Toast.makeText(this@SynapseActivity, "Mesh clip transmitted!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvClipBroadcastStatus.text = "Failed transmitting to $targetIp:$targetPort (unreachable)"
                }
            }
        }
    }

    private fun observeSynapseEngineState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    synapseEngine.serverState.collectLatest { isRunning ->
                        binding.switchServerToggle.isChecked = isRunning
                        updateServerStatusBadge(isRunning)
                    }
                }

                launch {
                    synapseEngine.latestClip.collectLatest { clip ->
                        if (clip != null && clip.content.isNotEmpty()) {
                            binding.tvLatestClipContent.text = clip.content
                            val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(clip.timestamp))
                            binding.tvLatestClipMeta.text = "Origin: ${clip.senderIp} · $timeStr"
                        }
                    }
                }
            }
        }
    }

    private class RemoteFilesAdapter : RecyclerView.Adapter<RemoteFilesAdapter.ViewHolder>() {

        private val items = mutableListOf<FileNode>()
        var onFolderClick: ((FileNode) -> Unit)? = null
        var onDownloadClick: ((FileNode, (Boolean) -> Unit) -> Unit)? = null

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun submitList(newItems: List<FileNode>) {
            items.clear()
            items.addAll(newItems.sortedWith(
                compareByDescending<FileNode> { it.isDirectory }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            ))
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSynapseFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemSynapseFileBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: FileNode) {
                binding.tvRemoteName.text = item.name

                val dateStr = if (item.lastModified > 0) dateFormat.format(Date(item.lastModified)) else ""
                val metaStr = if (item.isDirectory) {
                    "Directory ${if (dateStr.isNotEmpty()) "· $dateStr" else ""}"
                } else {
                    "${formatFileSize(item.size)} ${if (dateStr.isNotEmpty()) "· $dateStr" else ""}"
                }
                binding.tvRemoteMeta.text = metaStr.trim()

                binding.ivRemoteIcon.setImageResource(resolveIcon(item))

                if (item.isDirectory) {
                    binding.btnDownloadRemote.text = "Open"
                    binding.btnDownloadRemote.setIconResource(R.drawable.ic_folder)
                    binding.btnDownloadRemote.setOnClickListener {
                        onFolderClick?.invoke(item)
                    }
                    binding.root.setOnClickListener {
                        onFolderClick?.invoke(item)
                    }
                } else {
                    binding.btnDownloadRemote.text = "Download"
                    binding.btnDownloadRemote.setIconResource(R.drawable.ic_download)
                    binding.btnDownloadRemote.setOnClickListener {
                        binding.progressDownload.visibility = View.VISIBLE
                        binding.btnDownloadRemote.isEnabled = false
                        onDownloadClick?.invoke(item) {
                            binding.progressDownload.visibility = View.GONE
                            binding.btnDownloadRemote.isEnabled = true
                        }
                    }
                    binding.root.setOnClickListener {
                        binding.btnDownloadRemote.performClick()
                    }
                }
            }

            private fun resolveIcon(item: FileNode): Int {
                if (item.isDirectory) return R.drawable.ic_folder
                val ext = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                return when (ext) {
                    "apk", "xapk" -> R.drawable.ic_outline_apk
                    "pdf" -> R.drawable.ic_outline_pdf
                    "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp" -> R.drawable.ic_outline_code
                    "zip", "rar", "tar", "gz", "7z" -> R.drawable.ic_file_archive
                    "png", "jpg", "jpeg", "webp", "gif" -> R.drawable.ic_file_image
                    "mp3", "wav", "flac", "ogg" -> R.drawable.ic_file_audio
                    "mp4", "mkv", "mov", "webm" -> R.drawable.ic_file_video
                    else -> R.drawable.ic_fluent_default
                }
            }

            private fun formatFileSize(bytes: Long): String {
                if (bytes <= 0) return "0 B"
                val units = arrayOf("B", "KB", "MB", "GB", "TB")
                val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
            }
        }
    }
}
