package com.polymath.fs.viewers

import android.content.Intent
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.request.ImageRequest
import com.google.android.material.button.MaterialButton
import com.polymath.fs.R
import com.polymath.fs.core.BaseDynamicActivity
import com.polymath.fs.core.SystemBarHelper
import java.io.File

class ImageViewerActivity : BaseDynamicActivity() {

    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var btnClose: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var tvImageTitle: TextView
    private lateinit var tvImageDetails: TextView
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var tvCounter: TextView

    private val imageList = mutableListOf<String>()
    private var currentIndex = 0
    private var isOverlayVisible = true

    // Matrix Transformation & Zoom State
    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private var mode = NONE
    private val start = PointF()
    private val mid = PointF()
    private var oldDist = 1f
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 5f

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        SystemBarHelper.updateSystemBarAppearance(this)

        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        btnClose = findViewById(R.id.btnClose)
        btnShare = findViewById(R.id.btnShare)
        tvImageTitle = findViewById(R.id.tvImageTitle)
        tvImageDetails = findViewById(R.id.tvImageDetails)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        tvCounter = findViewById(R.id.tvCounter)

        btnClose.setOnClickListener { finish() }
        btnShare.setOnClickListener { shareCurrentImage() }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                displayImage(imageList[currentIndex])
            }
        }

        btnNext.setOnClickListener {
            if (currentIndex < imageList.lastIndex) {
                currentIndex++
                displayImage(imageList[currentIndex])
            }
        }

        val initialPath = intent.getStringExtra("path")
            ?: intent.getStringExtra("filePath")
            ?: intent.getStringExtra("FILE_PATH")
            ?: intent.dataString

        initImageGallery(initialPath)
        setupGestures()
    }

    private fun initImageGallery(initialPath: String?) {
        if (initialPath.isNullOrBlank()) {
            Toast.makeText(this, "No image specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (initialPath.startsWith("content://") || initialPath.startsWith("sftp://")) {
            imageList.add(initialPath)
            currentIndex = 0
        } else {
            val file = File(initialPath)
            val parent = file.parentFile
            if (parent != null && parent.isDirectory) {
                val siblings = parent.listFiles { f ->
                    val ext = f.extension.lowercase()
                    ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp" || ext == "gif" || ext == "bmp"
                }?.sortedBy { it.name.lowercase() }

                if (!siblings.isNullOrEmpty()) {
                    siblings.forEachIndexed { idx, f ->
                        imageList.add(f.absolutePath)
                        if (f.absolutePath == file.absolutePath) {
                            currentIndex = idx
                        }
                    }
                }
            }

            if (imageList.isEmpty()) {
                imageList.add(initialPath)
                currentIndex = 0
            }
        }

        displayImage(imageList[currentIndex])
    }

    private fun displayImage(path: String) {
        resetZoom()
        tvCounter.text = "${currentIndex + 1} / ${imageList.size}"
        btnPrev.isEnabled = currentIndex > 0
        btnNext.isEnabled = currentIndex < imageList.lastIndex

        val fileName = path.substringAfterLast('/').ifEmpty { "Image" }
        tvImageTitle.text = fileName

        progressBar.visibility = View.VISIBLE

        val dataToLoad: Any = if (path.startsWith("content://")) Uri.parse(path) else File(path)

        imageView.load(dataToLoad) {
            crossfade(true)
            listener(
                onSuccess = { _, result ->
                    progressBar.visibility = View.GONE
                    val drawable = result.drawable
                    val w = drawable.intrinsicWidth
                    val h = drawable.intrinsicHeight
                    val sizeStr = if (path.startsWith("content://")) "" else {
                        val f = File(path)
                        if (f.exists()) " • ${Formatter.formatFileSize(this@ImageViewerActivity, f.length())}" else ""
                    }
                    tvImageDetails.text = "${w} × ${h} px$sizeStr"
                    fitImageToScreen(w, h)
                },
                onError = { _, throwable ->
                    progressBar.visibility = View.GONE
                    tvImageDetails.text = "Failed to load: ${throwable.throwable.message}"
                    Toast.makeText(this@ImageViewerActivity, "Cannot render image", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun fitImageToScreen(imageWidth: Int, imageHeight: Int) {
        if (imageWidth <= 0 || imageHeight <= 0) return
        imageView.post {
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()
            if (viewWidth <= 0 || viewHeight <= 0) return@post

            matrix.reset()
            val scaleX = viewWidth / imageWidth
            val scaleY = viewHeight / imageHeight
            val fitScale = minOf(scaleX, scaleY)

            val dx = (viewWidth - imageWidth * fitScale) / 2f
            val dy = (viewHeight - imageHeight * fitScale) / 2f

            matrix.postScale(fitScale, fitScale)
            matrix.postTranslate(dx, dy)
            imageView.imageMatrix = matrix
            currentScale = 1f
        }
    }

    private fun resetZoom() {
        matrix.reset()
        currentScale = 1f
        imageView.imageMatrix = matrix
    }

    private fun setupGestures() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val targetScale = currentScale * scaleFactor
                if (targetScale in minScale..maxScale) {
                    currentScale = targetScale
                    matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    imageView.imageMatrix = matrix
                }
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleOverlays()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.2f) {
                    // Zoom out to 1x
                    displayImage(imageList[currentIndex])
                } else {
                    // Zoom in to 2.5x at tap point
                    matrix.postScale(2.5f, 2.5f, e.x, e.y)
                    currentScale = 2.5f
                    imageView.imageMatrix = matrix
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (currentScale <= 1.1f && e1 != null) {
                    val deltaX = e2.x - e1.x
                    if (deltaX < -150 && Math.abs(velocityX) > 200 && currentIndex < imageList.lastIndex) {
                        currentIndex++
                        displayImage(imageList[currentIndex])
                        return true
                    } else if (deltaX > 150 && Math.abs(velocityX) > 200 && currentIndex > 0) {
                        currentIndex--
                        displayImage(imageList[currentIndex])
                        return true
                    }
                }
                return false
            }
        })

        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(mid, event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG && currentScale > 1.05f) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - start.x, event.y - start.y)
                        imageView.imageMatrix = matrix
                    }
                }
            }
            true
        }
    }

    private fun toggleOverlays() {
        isOverlayVisible = !isOverlayVisible
        val vis = if (isOverlayVisible) View.VISIBLE else View.GONE
        topBar.visibility = vis
        bottomBar.visibility = vis
    }

    private fun shareCurrentImage() {
        if (currentIndex in imageList.indices) {
            val path = imageList[currentIndex]
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                if (path.startsWith("content://")) {
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                } else {
                    val f = File(path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@ImageViewerActivity,
                        "$packageName.fileprovider",
                        f
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(intent, "Share Image"))
        }
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
}
