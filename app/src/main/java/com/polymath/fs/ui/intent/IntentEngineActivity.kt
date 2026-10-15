package com.polymath.fs.ui.intent

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.MainActivity
import com.polymath.fs.PolymathApp
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.IntentEngine
import com.polymath.fs.databinding.ActivityIntentEngineBinding
import com.polymath.fs.databinding.ItemIntentResultBinding
import com.polymath.fs.viewers.EditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

class IntentEngineActivity : BaseDynamicActivity() {

    private lateinit var binding: ActivityIntentEngineBinding
    private lateinit var intentEngine: IntentEngine
    private val resultsAdapter = IntentResultsAdapter()
    private var searchJob: Job? = null

    data class IntentSearchResult(
        val file: File,
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val reasons: List<String>,
        val score: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntentEngineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as PolymathApp
        intentEngine = app.intentEngine

        applyEdgeToEdgeInsets()
        setupViews()
        setupSuggestionChips()

        // Default: Show recent intent items or initial scan
        executeIntentSearch("modified today", isSuggestion = true)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootIntentLayout) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top + 12,
                binding.topBar.paddingRight,
                12
            )
            binding.rvIntentResults.setPadding(
                binding.rvIntentResults.paddingLeft,
                binding.rvIntentResults.paddingTop,
                binding.rvIntentResults.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.rvIntentResults.layoutManager = LinearLayoutManager(this)
        binding.rvIntentResults.adapter = resultsAdapter

        resultsAdapter.onItemClick = { item ->
            lifecycleScope.launch {
                intentEngine.logIntent("opened", item.path, binding.etSearchQuery.text.toString())
            }
            if (item.isDirectory) {
                navigateToFolderInMain(item.path)
            } else {
                openInEditor(item.path)
            }
        }

        resultsAdapter.onLocateClick = { item ->
            lifecycleScope.launch {
                intentEngine.logIntent("locate", item.path, "locate_action")
            }
            val parentPath = item.file.parent ?: item.path
            navigateToFolderInMain(parentPath)
        }

        binding.btnClearQuery.setOnClickListener {
            binding.etSearchQuery.setText("")
            binding.chipGroupSuggestions.clearCheck()
            showEmptyState("Type your query or pick a suggestion chip above.")
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearchQuery.text.toString().trim()
                if (query.isNotEmpty()) {
                    executeIntentSearch(query)
                }
                true
            } else {
                false
            }
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearQuery.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                searchJob?.cancel()
                if (query.length >= 2) {
                    searchJob = lifecycleScope.launch {
                        delay(350)
                        executeIntentSearch(query)
                    }
                } else if (query.isEmpty()) {
                    showEmptyState("Type in human language or pick a suggestion chip above.")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSuggestionChips() {
        binding.chipModifiedToday.setOnClickListener {
            binding.etSearchQuery.setText("Modified Today")
            executeIntentSearch("modified today", isSuggestion = true)
        }
        binding.chipDownloadedYesterday.setOnClickListener {
            binding.etSearchQuery.setText("Downloaded Yesterday")
            executeIntentSearch("downloaded yesterday", isSuggestion = true)
        }
        binding.chipKotlinSource.setOnClickListener {
            binding.etSearchQuery.setText("Kotlin Source Files")
            executeIntentSearch("kotlin source files", isSuggestion = true)
        }
        binding.chipApkPackages.setOnClickListener {
            binding.etSearchQuery.setText("APK Packages")
            executeIntentSearch("apk packages", isSuggestion = true)
        }
        binding.chipRecentDocs.setOnClickListener {
            binding.etSearchQuery.setText("Recent Documents")
            executeIntentSearch("recent documents", isSuggestion = true)
        }
    }

    private fun executeIntentSearch(rawQuery: String, isSuggestion: Boolean = false) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        binding.progressBarIntent.visibility = View.VISIBLE
        binding.tvStatusSummary.text = "Resolving semantic intent for: \"$query\"..."

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val results = withContext(Dispatchers.IO) {
                performSemanticScan(query)
            }
            val elapsed = System.currentTimeMillis() - startTime

            binding.progressBarIntent.visibility = View.GONE
            if (results.isEmpty()) {
                showEmptyState("No files matched intent \"$query\". Try another search or chip.")
                binding.tvStatusSummary.text = "0 results found · ${elapsed}ms"
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvIntentResults.visibility = View.VISIBLE
                resultsAdapter.submitList(results)
                binding.tvStatusSummary.text = "Matched ${results.size} files & folders · ${elapsed}ms"
            }
        }
    }

    private fun performSemanticScan(query: String): List<IntentSearchResult> {
        val q = query.lowercase(Locale.ROOT)
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }

        // 1. Resolve through IntentEngine memory core
        val intentMatches = try {
            intentEngine.resolveIntent(query)
        } catch (_: Exception) {
            emptyMap()
        }

        // 2. Evaluate Temporal & Semantic criteria
        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfYesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfYesterday = startOfToday - 1
        val last7Days = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)

        val isTodayQuery = q.contains("today")
        val isYesterdayQuery = q.contains("yesterday")
        val isRecentQuery = q.contains("recent") || q.contains("latest")
        val isKotlinQuery = q.contains("kotlin") || q.contains(".kt")
        val isApkQuery = q.contains("apk") || q.contains("package")
        val isDocQuery = q.contains("doc") || q.contains("document") || q.contains("pdf") || q.contains("text")
        val isDownloadQuery = q.contains("download")

        val docExtensions = setOf("pdf", "doc", "docx", "txt", "md", "rtf", "xlsx", "xls", "pptx", "epub", "csv")
        val codeExtensions = setOf("kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "html", "css", "json", "xml", "sh", "yaml", "yml")

        // 3. Scan directory trees
        val searchRoots = mutableListOf<File>()
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage != null && primaryStorage.exists()) {
            if (isDownloadQuery) {
                val dlDir = File(primaryStorage, "Download")
                if (dlDir.exists()) searchRoots.add(dlDir)
            }
            if (isDocQuery) {
                val docDir = File(primaryStorage, "Documents")
                if (docDir.exists()) searchRoots.add(docDir)
            }
            searchRoots.add(primaryStorage)
        }

        val internalFiles = filesDir
        if (internalFiles != null && internalFiles.exists()) {
            searchRoots.add(internalFiles)
        }

        val resultMap = mutableMapOf<String, IntentSearchResult>()

        // Seed with intent logs matches first
        intentMatches.forEach { (filePath, reasons) ->
            val file = File(filePath)
            if (file.exists()) {
                val badges = reasons.toMutableList()
                if (badges.isEmpty()) badges.add("Accessed Recently")
                resultMap[filePath] = IntentSearchResult(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory,
                    reasons = badges,
                    score = 120
                )
            }
        }

        // BFS directory scan with bounded depth
        val queue = ArrayDeque<Pair<File, Int>>()
        for (root in searchRoots) {
            queue.add(Pair(root, 0))
        }

        val maxScanned = 600
        var scannedCount = 0

        while (queue.isNotEmpty() && scannedCount < maxScanned) {
            val (currentDir, depth) = queue.removeFirst()
            scannedCount++

            if (depth > 4) continue
            val dirPath = currentDir.absolutePath
            if (dirPath.startsWith("/proc") || dirPath.startsWith("/sys") || dirPath.startsWith("/dev")) continue

            val children = try {
                currentDir.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            for (child in children) {
                if (child.name.startsWith(".")) continue

                var score = 0
                val reasons = mutableListOf<String>()
                val nameLower = child.name.lowercase(Locale.ROOT)
                val ext = child.extension.lowercase(Locale.ROOT)
                val isDir = child.isDirectory

                // Temporal checks
                if (isTodayQuery && child.lastModified() >= startOfToday) {
                    score += 60
                    reasons.add("Modified Today")
                } else if (isYesterdayQuery && child.lastModified() in startOfYesterday..endOfYesterday) {
                    score += 60
                    reasons.add("Downloaded Yesterday")
                } else if (isRecentQuery && child.lastModified() >= last7Days) {
                    score += 35
                    reasons.add("Recent Activity")
                }

                // Extension & Category checks
                if (isKotlinQuery && (ext == "kt" || ext == "kts")) {
                    score += 55
                    reasons.add("Kotlin Source")
                }
                if (isApkQuery && (ext == "apk" || ext == "xapk" || ext == "apks")) {
                    score += 55
                    reasons.add("APK Package")
                }
                if (isDocQuery && docExtensions.contains(ext)) {
                    score += 45
                    reasons.add("Document")
                }

                // Term & Keyword matches
                val matchesKeyword = terms.any { term ->
                    term.length >= 2 && (nameLower.contains(term) || (!isDir && ext == term))
                }
                if (matchesKeyword) {
                    score += 40
                    reasons.add("Keyword Match")
                }

                // Download folder origin
                if (child.absolutePath.contains("/Download") && (isDownloadQuery || isYesterdayQuery || isTodayQuery)) {
                    score += 20
                    if (!reasons.contains("Downloaded Yesterday")) {
                        reasons.add("Download Dir")
                    }
                }

                // Check if already had intentEngine log
                val existingIntent = intentMatches[child.absolutePath]
                if (existingIntent != null) {
                    score += 80
                    existingIntent.forEach { r -> if (!reasons.contains(r)) reasons.add(r) }
                }

                if (score > 0 && reasons.isNotEmpty()) {
                    val existing = resultMap[child.absolutePath]
                    if (existing == null || existing.score < score) {
                        resultMap[child.absolutePath] = IntentSearchResult(
                            file = child,
                            name = child.name,
                            path = child.absolutePath,
                            size = if (isDir) 0L else child.length(),
                            lastModified = child.lastModified(),
                            isDirectory = isDir,
                            reasons = reasons,
                            score = score
                        )
                    }
                }

                if (isDir && depth < 4) {
                    queue.add(Pair(child, depth + 1))
                }
            }
        }

        return resultMap.values.sortedWith(
            compareByDescending<IntentSearchResult> { it.score }
                .thenByDescending { it.lastModified }
        )
    }

    private fun showEmptyState(message: String) {
        binding.rvIntentResults.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.tvEmptyDescription.text = message
    }

    private fun navigateToFolderInMain(folderPath: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_NAVIGATE_PATH", folderPath)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openInEditor(filePath: String) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("path", filePath)
            putExtra("filePath", filePath)
        }
        startActivity(intent)
    }

    private class IntentResultsAdapter : RecyclerView.Adapter<IntentResultsAdapter.ViewHolder>() {

        private val items = mutableListOf<IntentSearchResult>()
        var onItemClick: ((IntentSearchResult) -> Unit)? = null
        var onLocateClick: ((IntentSearchResult) -> Unit)? = null

        private val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

        fun submitList(newItems: List<IntentSearchResult>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemIntentResultBinding.inflate(
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

        inner class ViewHolder(private val binding: ItemIntentResultBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: IntentSearchResult) {
                binding.tvFileName.text = item.name
                binding.tvFilePath.text = item.path

                val formattedDate = dateFormat.format(item.lastModified)
                val metaText = if (item.isDirectory) {
                    "Directory · $formattedDate"
                } else {
                    "${formatFileSize(item.size)} · $formattedDate"
                }
                binding.tvFileMeta.text = metaText

                binding.ivFileIcon.setImageResource(resolveIcon(item))

                // Populate Reason Badges
                binding.layoutBadges.removeAllViews()
                for (reason in item.reasons) {
                    val badgeView = createBadgeView(binding.root.context, reason)
                    binding.layoutBadges.addView(badgeView)
                }

                binding.root.setOnClickListener {
                    onItemClick?.invoke(item)
                }

                binding.btnLocate.setOnClickListener {
                    onLocateClick?.invoke(item)
                }
            }

            private fun resolveIcon(item: IntentSearchResult): Int {
                if (item.isDirectory) return R.drawable.ic_folder
                val ext = item.file.extension.lowercase(Locale.ROOT)
                return when (ext) {
                    "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "html", "css", "json", "xml", "sh" -> R.drawable.ic_outline_code
                    "apk", "xapk", "apks" -> R.drawable.ic_outline_apk
                    "pdf" -> R.drawable.ic_outline_pdf
                    "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_file_archive
                    "jpg", "jpeg", "png", "webp", "gif", "svg" -> R.drawable.ic_file_image
                    "mp3", "wav", "ogg", "flac", "m4a" -> R.drawable.ic_file_audio
                    "mp4", "mkv", "mov", "webm" -> R.drawable.ic_file_video
                    else -> R.drawable.ic_fluent_default
                }
            }

            private fun createBadgeView(context: android.content.Context, reason: String): TextView {
                val tv = TextView(context).apply {
                    text = reason
                    textSize = 10f
                    setPadding(16, 6, 16, 6)
                    val lp = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = 12
                    layoutParams = lp
                }

                val (bgColor, strokeColor, textColor) = when {
                    reason.contains("Today") || reason.contains("Yesterday") ->
                        Triple(Color.parseColor("#0e3854"), Color.parseColor("#38bdf8"), Color.parseColor("#38bdf8"))
                    reason.contains("Kotlin") || reason.contains("Code") ->
                        Triple(Color.parseColor("#3b185f"), Color.parseColor("#c084fc"), Color.parseColor("#c084fc"))
                    reason.contains("APK") || reason.contains("Package") ->
                        Triple(Color.parseColor("#064e3b"), Color.parseColor("#34d399"), Color.parseColor("#34d399"))
                    reason.contains("Document") || reason.contains("PDF") ->
                        Triple(Color.parseColor("#451a03"), Color.parseColor("#fbbf24"), Color.parseColor("#fbbf24"))
                    reason.contains("Accessed") || reason.contains("Action") ->
                        Triple(Color.parseColor("#3f123d"), Color.parseColor("#f472b6"), Color.parseColor("#f472b6"))
                    else ->
                        Triple(Color.parseColor("#1e1b4b"), Color.parseColor("#818cf8"), Color.parseColor("#818cf8"))
                }

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f
                    setColor(bgColor)
                    setStroke(2, strokeColor)
                }

                tv.background = drawable
                tv.setTextColor(textColor)
                return tv
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
