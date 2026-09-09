package com.polymath.fs.ui.canvas

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.polymath.fs.core.canvas.physics.ForceSimulationEngine
import com.polymath.fs.domain.canvas.heatmap.CanvasHeatmapEvaluator
import com.polymath.fs.domain.canvas.models.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CognitiveCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Viewport Camera
    val viewport = CanvasViewport()

    // Data Graph
    private val nodes = mutableListOf<CanvasNode>()
    private val edges = mutableListOf<CanvasEdge>()

    // Physics Engine
    private val physicsEngine = ForceSimulationEngine()
    private var isPhysicsRunning = false

    // Snap to grid movement system
    var isSnapToGridEnabled: Boolean = true
    var snapGridSize: Float = 75f
    var isVisualGridOverlayVisible: Boolean = true
    private var lastSnappedGridX = 0f
    private var lastSnappedGridY = 0f
    private var isGridSnappingActive = false
    private var dragNodeInitialX = 0f
    private var dragNodeInitialY = 0f

    // Zoom listener & precision manual scale control
    var onScaleChangedListener: ((Float) -> Unit)? = null

    // Search state & navigation
    private var currentSearchQuery: String = ""
    private val searchResults = mutableListOf<CanvasNode>()
    private var currentSearchIndex: Int = -1
    var onSearchResultsChanged: ((query: String, count: Int, currentIndex: Int) -> Unit)? = null

    // Interactive Link Mode
    var isLinkModeActive: Boolean = false
    private var linkStartNode: CanvasNode? = null
    private var linkCurrentWorldX: Float = 0f
    private var linkCurrentWorldY: Float = 0f

    // Activity Heatmap Layer
    var isHeatmapVisible: Boolean = false
    private var heatScores: Map<String, Float> = emptyMap()
    var onHeatmapToggledListener: ((Boolean) -> Unit)? = null

    // Interaction Callbacks
    var onNodeSelectedListener: ((CanvasNode) -> Unit)? = null
    var onSelectionClearedListener: (() -> Unit)? = null
    var onNodeDoubleClickListener: ((CanvasNode) -> Unit)? = null
    var onNodeLongClickListener: ((CanvasNode) -> Unit)? = null
    var onNodeMovedListener: ((CanvasNode) -> Unit)? = null
    var onNodeMovedWithInitialPositionListener: ((CanvasNode, Float, Float) -> Unit)? = null
    var onCanvasSelectionChangedListener: ((List<CanvasNode>) -> Unit)? = null
    var onNodesLinkedListener: ((CanvasNode, CanvasNode) -> Unit)? = null

    // Touch & Drag State
    private var activeDraggedNode: CanvasNode? = null
    private var isDraggingNode = false
    private var hasMovedNode = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Rendering Paints
    private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E38BDF8")
        style = Paint.Style.FILL
    }
    private val visualGridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1538BDF8")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val snapTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#8038BDF8")
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }
    private val snapLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#4D38BDF8")
    }
    private val themeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val edgeParentChildPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        color = Color.parseColor("#5038BDF8")
    }
    private val edgeParentToParentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        color = Color.parseColor("#80F59E0B") // Amber glow for parent-parent architecture
    }
    private val edgeParentToChildPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.8f
        style = Paint.Style.STROKE
        color = Color.parseColor("#8006B6D4") // Cyan for parent-child tree
    }
    private val edgeSimilarityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.8f
        style = Paint.Style.STROKE
        color = Color.parseColor("#45A855F7")
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val edgeUserLinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        color = Color.parseColor("#8010B981")
    }
    private val tempLinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        color = Color.parseColor("#38BDF8")
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }
    private val nodeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val nodeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    private val heatmapGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val heatFlamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    private val edgeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    // Mini-Map Paints
    private val miniMapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 15, 23, 42) // Transparent frosted dark
        style = Paint.Style.FILL
    }
    private val miniMapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 56, 189, 248) // Translucent cyan border
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val miniMapNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val miniMapViewfrustumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 56, 189, 248)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val miniMapTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7038BDF8")
        textSize = 14f
    }

    // Reusable structures
    private val edgePath = Path()
    private val tempBounds = RectF()
    private val miniMapBounds = RectF()

    // Gestures
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY

                val oldScale = viewport.scale
                viewport.scale *= scaleFactor
                viewport.clampScale(0.20f, 4.0f)
                val newScale = viewport.scale

                val scaleDelta = newScale / oldScale
                viewport.translationX = focusX - (focusX - viewport.translationX) * scaleDelta
                viewport.translationY = focusY - (focusY - viewport.translationY) * scaleDelta

                onScaleChangedListener?.invoke(newScale)
                invalidate()
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val worldX = viewport.toWorldX(e.x)
                val worldY = viewport.toWorldY(e.y)
                val hitNode = findNodeAt(worldX, worldY)
                if (hitNode != null) {
                    onNodeLongClickListener?.invoke(hitNode)
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val worldX = viewport.toWorldX(e.x)
                val worldY = viewport.toWorldY(e.y)
                val clickedNode = findNodeAt(worldX, worldY)
                if (clickedNode != null) {
                    onNodeDoubleClickListener?.invoke(clickedNode)
                    return true
                } else {
                    resetViewAnimated()
                    return true
                }
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val worldX = viewport.toWorldX(e.x)
                val worldY = viewport.toWorldY(e.y)
                val clickedNode = findNodeAt(worldX, worldY)
                if (clickedNode != null) {
                    val willBeSelected = !clickedNode.isSelected
                    nodes.forEach { it.isSelected = false }
                    clickedNode.isSelected = willBeSelected
                    if (willBeSelected) {
                        onNodeSelectedListener?.invoke(clickedNode)
                        onCanvasSelectionChangedListener?.invoke(listOf(clickedNode))
                    } else {
                        onSelectionClearedListener?.invoke()
                        onCanvasSelectionChangedListener?.invoke(emptyList())
                    }
                } else {
                    nodes.forEach { it.isSelected = false }
                    onSelectionClearedListener?.invoke()
                    onCanvasSelectionChangedListener?.invoke(emptyList())
                }
                invalidate()
                return true
            }
        })
    }

    fun clearSelection() {
        nodes.forEach { it.isSelected = false }
        onSelectionClearedListener?.invoke()
        onCanvasSelectionChangedListener?.invoke(emptyList())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (viewport.translationX == 0f && viewport.translationY == 0f) {
            viewport.translationX = w / 2f
            viewport.translationY = h / 2f
        }
    }

    fun setGraphData(newNodes: List<CanvasNode>, newEdges: List<CanvasEdge>, autoArrange: Boolean = false) {
        nodes.clear()
        nodes.addAll(newNodes)
        edges.clear()
        edges.addAll(newEdges)

        if (autoArrange) {
            physicsEngine.initializePositions(nodes, 0f, 0f)
            startPhysicsSimulation()
        } else {
            invalidate()
        }
    }

    fun startPhysicsSimulation() {
        if (isPhysicsRunning) return
        isPhysicsRunning = true
        post(physicsStepRunnable)
    }

    private val physicsStepRunnable = object : Runnable {
        override fun run() {
            if (!isPhysicsRunning) return
            val isStillMoving = physicsEngine.step(nodes, edges, 0f, 0f)
            invalidate()

            if (isStillMoving || isDraggingNode) {
                postOnAnimation(this)
            } else {
                isPhysicsRunning = false
            }
        }
    }

    fun resetViewAnimated() {
        val startX = viewport.translationX
        val startY = viewport.translationY
        val startScale = viewport.scale

        val targetX = width / 2f
        val targetY = height / 2f
        val targetScale = 1.0f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                viewport.translationX = startX + (targetX - startX) * fraction
                viewport.translationY = startY + (targetY - startY) * fraction
                viewport.scale = startScale + (targetScale - startScale) * fraction
                invalidate()
            }
            start()
        }
    }

    fun focusOnNode(node: CanvasNode, targetScale: Float = 1.3f) {
        val startX = viewport.translationX
        val startY = viewport.translationY
        val startScale = viewport.scale

        val targetX = width / 2f - node.x * targetScale
        val targetY = height / 2f - node.y * targetScale

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                viewport.translationX = startX + (targetX - startX) * fraction
                viewport.translationY = startY + (targetY - startY) * fraction
                viewport.scale = startScale + (targetScale - startScale) * fraction
                invalidate()
            }
            start()
        }
    }

    fun searchAndHighlight(query: String): Int {
        currentSearchQuery = query.trim()
        searchResults.clear()
        currentSearchIndex = -1

        if (currentSearchQuery.isEmpty()) {
            nodes.forEach { it.isHighlighted = false }
            onSearchResultsChanged?.invoke("", 0, -1)
            invalidate()
            return 0
        }

        val lower = currentSearchQuery.lowercase()
        for (node in nodes) {
            val matchesName = node.fileNode.name.lowercase().contains(lower)
            val matchesExt = node.fileNode.extension.lowercase().contains(lower)
            val matchesCluster = node.clusterTag.lowercase().contains(lower)
            node.isHighlighted = matchesName || matchesExt || matchesCluster
            if (node.isHighlighted) {
                searchResults.add(node)
            }
        }

        if (searchResults.isNotEmpty()) {
            currentSearchIndex = 0
            focusOnNode(searchResults[0])
        }

        onSearchResultsChanged?.invoke(currentSearchQuery, searchResults.size, currentSearchIndex)
        invalidate()
        return searchResults.size
    }

    fun focusNextSearchResult(): CanvasNode? {
        if (searchResults.isEmpty()) return null
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        val node = searchResults[currentSearchIndex]
        focusOnNode(node)
        onSearchResultsChanged?.invoke(currentSearchQuery, searchResults.size, currentSearchIndex)
        return node
    }

    fun focusPreviousSearchResult(): CanvasNode? {
        if (searchResults.isEmpty()) return null
        currentSearchIndex = if (currentSearchIndex <= 0) searchResults.size - 1 else currentSearchIndex - 1
        val node = searchResults[currentSearchIndex]
        focusOnNode(node)
        onSearchResultsChanged?.invoke(currentSearchQuery, searchResults.size, currentSearchIndex)
        return node
    }

    fun smartArrange(onFinished: (() -> Unit)? = null) {
        if (nodes.isEmpty()) {
            onFinished?.invoke()
            return
        }

        // Apply metadata and directory clustered force-directed layout
        physicsEngine.arrangeByClusteredMetadata(nodes, 0f, 0f)

        // Make sure directory anchors stay pinned, files animate
        for (node in nodes) {
            if (node.id.startsWith("root_")) {
                node.isPinned = true
            }
        }

        startPhysicsSimulation()
        resetViewAnimated()

        // After simulation settling, align to coordinate grid if snap is enabled
        postDelayed({
            if (isSnapToGridEnabled) {
                for (node in nodes) {
                    node.x = (node.x / snapGridSize).roundToInt() * snapGridSize
                    node.y = (node.y / snapGridSize).roundToInt() * snapGridSize
                    node.vx = 0f
                    node.vy = 0f
                }
                invalidate()
            }
            onFinished?.invoke()
        }, 850)
    }

    fun getSelectedNodes(): List<CanvasNode> = nodes.filter { it.isSelected }

    fun getAllNodes(): List<CanvasNode> = nodes

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val worldX = viewport.toWorldX(event.x)
        val worldY = viewport.toWorldY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                hasMovedNode = false

                val hitNode = findNodeAt(worldX, worldY)
                if (hitNode != null) {
                    if (isLinkModeActive) {
                        linkStartNode = hitNode
                        linkCurrentWorldX = worldX
                        linkCurrentWorldY = worldY
                    } else {
                        activeDraggedNode = hitNode
                        isDraggingNode = true
                        hitNode.isPinned = true
                        dragNodeInitialX = hitNode.x
                        dragNodeInitialY = hitNode.y
                        lastSnappedGridX = (hitNode.x / snapGridSize).roundToInt() * snapGridSize
                        lastSnappedGridY = (hitNode.y / snapGridSize).roundToInt() * snapGridSize
                        startPhysicsSimulation()
                    }
                } else {
                    isDraggingNode = false
                    linkStartNode = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (isLinkModeActive && linkStartNode != null) {
                    linkCurrentWorldX = worldX
                    linkCurrentWorldY = worldY
                    invalidate()
                } else if (isDraggingNode && activeDraggedNode != null) {
                    hasMovedNode = true
                    activeDraggedNode?.let { node ->
                        var targetX = worldX
                        var targetY = worldY

                        if (isSnapToGridEnabled) {
                            val snappedX = (targetX / snapGridSize).roundToInt() * snapGridSize
                            val snappedY = (targetY / snapGridSize).roundToInt() * snapGridSize

                            if (snappedX != lastSnappedGridX || snappedY != lastSnappedGridY) {
                                lastSnappedGridX = snappedX
                                lastSnappedGridY = snappedY
                                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            targetX = snappedX
                            targetY = snappedY
                            isGridSnappingActive = true
                        }

                        // Apply spring-damper drag physics follower
                        physicsEngine.applyDragSpring(node, targetX, targetY)
                    }
                    startPhysicsSimulation()
                } else if (!scaleGestureDetector.isInProgress) {
                    viewport.translationX += dx
                    viewport.translationY += dy
                    invalidate()
                }

                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isLinkModeActive && linkStartNode != null) {
                    val targetNode = findNodeAt(worldX, worldY)
                    if (targetNode != null && targetNode.id != linkStartNode?.id) {
                        linkStartNode?.let { src ->
                            onNodesLinkedListener?.invoke(src, targetNode)
                        }
                    }
                    linkStartNode = null
                    invalidate()
                }

                if (activeDraggedNode != null) {
                    val node = activeDraggedNode!!
                    if (isSnapToGridEnabled) {
                        node.x = (node.x / snapGridSize).roundToInt() * snapGridSize
                        node.y = (node.y / snapGridSize).roundToInt() * snapGridSize
                        node.vx = 0f
                        node.vy = 0f
                    } else {
                        // Allow node to carry residual spring-damper momentum and settle naturally
                        node.isPinned = false
                    }
                    isGridSnappingActive = false
                    if (hasMovedNode) {
                        onNodeMovedListener?.invoke(node)
                        onNodeMovedWithInitialPositionListener?.invoke(node, dragNodeInitialX, dragNodeInitialY)
                    }
                    activeDraggedNode = null
                }
                isDraggingNode = false
            }
        }
        return true
    }

    private fun findNodeAt(worldX: Float, worldY: Float): CanvasNode? {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            val dx = worldX - node.x
            val dy = worldY - node.y
            if (dx * dx + dy * dy <= node.radius * node.radius * 1.5f) {
                return node
            }
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Adaptive Dot Grid
        drawBackgroundGrid(canvas)

        // Save canvas for Camera Matrix Transformations
        canvas.save()
        canvas.translate(viewport.translationX, viewport.translationY)
        canvas.scale(viewport.scale, viewport.scale)

        // 2. Draw Connections / Relationship Edges
        drawEdges(canvas)

        // Draw magnetic snap-to-grid movement alignment crosshair & target bounds
        if (isDraggingNode && isSnapToGridEnabled && activeDraggedNode != null) {
            val nodeR = activeDraggedNode!!.radius
            canvas.drawCircle(lastSnappedGridX, lastSnappedGridY, nodeR + 10f, snapTargetPaint)
            canvas.drawLine(lastSnappedGridX - nodeR * 2.2f, lastSnappedGridY, lastSnappedGridX + nodeR * 2.2f, lastSnappedGridY, snapLinePaint)
            canvas.drawLine(lastSnappedGridX, lastSnappedGridY - nodeR * 2.2f, lastSnappedGridX, lastSnappedGridY + nodeR * 2.2f, snapLinePaint)
        }

        // 3. Draw Active Interactive Link Creation Line
        if (isLinkModeActive && linkStartNode != null) {
            val start = linkStartNode!!
            canvas.drawLine(start.x, start.y, linkCurrentWorldX, linkCurrentWorldY, tempLinkPaint)
        }

        // 4. Draw File Nodes
        drawNodes(canvas)

        canvas.restore()

        // 5. Draw Transparent Mini-Map HUD Overlay in corner
        drawMiniMap(canvas)
    }

    private fun drawBackgroundGrid(canvas: Canvas) {
        val gridSize = snapGridSize * viewport.scale
        if (gridSize < 12f) return

        val offsetX = viewport.translationX % gridSize
        val offsetY = viewport.translationY % gridSize

        // If visual grid overlay is enabled, draw subtle grid guidelines to visualize snap boundaries
        if (isVisualGridOverlayVisible) {
            var lx = offsetX
            while (lx < width) {
                canvas.drawLine(lx, 0f, lx, height.toFloat(), visualGridLinePaint)
                lx += gridSize
            }
            var ly = offsetY
            while (ly < height) {
                canvas.drawLine(0f, ly, width.toFloat(), ly, visualGridLinePaint)
                ly += gridSize
            }
        }

        var x = offsetX
        while (x < width) {
            var y = offsetY
            while (y < height) {
                canvas.drawCircle(x, y, 2.5f, gridDotPaint)
                y += gridSize
            }
            x += gridSize
        }
    }

    private fun drawEdges(canvas: Canvas) {
        val nodeMap = nodes.associateBy { it.id }

        for (edge in edges) {
            val source = nodeMap[edge.sourceNodeId] ?: continue
            val target = nodeMap[edge.targetNodeId] ?: continue

            val paint = when (edge.relationType) {
                CanvasRelationType.USER_LINK -> edgeUserLinkPaint
                CanvasRelationType.SIMILARITY -> edgeSimilarityPaint
                CanvasRelationType.PARENT_TO_PARENT -> edgeParentToParentPaint
                CanvasRelationType.PARENT_TO_CHILD -> edgeParentToChildPaint
                else -> edgeParentChildPaint
            }

            edgePath.reset()
            edgePath.moveTo(source.x, source.y)

            // Curved quadratic / cubic bezier connection
            val midX = (source.x + target.x) / 2f
            val midY = (source.y + target.y) / 2f
            val curveIntensity = 0.18f
            val dx = target.x - source.x
            val dy = target.y - source.y
            val ctrlX = midX - dy * curveIntensity
            val ctrlY = midY + dx * curveIntensity

            edgePath.quadTo(ctrlX, ctrlY, target.x, target.y)
            canvas.drawPath(edgePath, paint)

            // Label on edge if present
            if (edge.label.isNotEmpty()) {
                canvas.drawText(edge.label, ctrlX, ctrlY - 8f, edgeLabelPaint)
            }
        }
    }

    private fun drawNodes(canvas: Canvas) {
        val isSearchActive = currentSearchQuery.isNotEmpty()

        // If Heatmap is enabled, ensure scores are populated
        if (isHeatmapVisible && heatScores.isEmpty() && nodes.isNotEmpty()) {
            heatScores = CanvasHeatmapEvaluator.computeHeatScores(nodes)
        }

        for (node in nodes) {
            val radius = node.radius
            val isMatch = node.isHighlighted

            // If search active, dim non-matching nodes for clear visual pop
            val alphaMultiplier = if (!isSearchActive || isMatch) 1.0f else 0.22f

            // Activity Heatmap Thermal Halo Layer
            if (isHeatmapVisible) {
                val heat = heatScores[node.id] ?: 0.1f
                val heatColor = CanvasHeatmapEvaluator.getHeatColor(heat, alpha = (140 * alphaMultiplier).toInt())
                heatmapGlowPaint.color = heatColor
                val heatRadius = radius + 18f + (heat * 24f)
                canvas.drawCircle(node.x, node.y, heatRadius, heatmapGlowPaint)

                if (heat >= 0.70f) {
                    canvas.drawText("🔥", node.x, node.y - radius - 10f, heatFlamePaint)
                }
            }

            // Glow if search match or selected
            if (isMatch) {
                nodeGlowPaint.color = Color.parseColor("#F59E0B") // Amber glow for search
                nodeGlowPaint.alpha = (230 * alphaMultiplier).toInt()
                canvas.drawCircle(node.x, node.y, radius + 14f, nodeGlowPaint)
            } else if (node.isSelected) {
                nodeGlowPaint.color = Color.parseColor("#8038BDF8")
                nodeGlowPaint.alpha = (180 * alphaMultiplier).toInt()
                canvas.drawCircle(node.x, node.y, radius + 8f, nodeGlowPaint)
            }

            // Outer ring
            val ringColor = when {
                isMatch -> Color.parseColor("#FBBF24")
                node.isSelected -> Color.parseColor("#38BDF8")
                node.themeColor != null -> node.color
                else -> Color.parseColor("#334155")
            }
            nodeStrokePaint.color = ringColor
            nodeStrokePaint.alpha = (255 * alphaMultiplier).toInt()
            canvas.drawCircle(node.x, node.y, radius, nodeStrokePaint)

            // Inner fill
            nodeBodyPaint.color = node.color
            nodeBodyPaint.alpha = ((if (isMatch) 245 else 210) * alphaMultiplier).toInt()
            canvas.drawCircle(node.x, node.y, radius - 3f, nodeBodyPaint)

            // Directory indicator ring
            if (node.nodeType == CanvasNodeType.DIRECTORY) {
                nodeStrokePaint.color = Color.WHITE
                nodeStrokePaint.strokeWidth = 2f
                nodeStrokePaint.alpha = (255 * alphaMultiplier).toInt()
                canvas.drawCircle(node.x, node.y, radius * 0.45f, nodeStrokePaint)
                nodeStrokePaint.strokeWidth = 3f
            }

            // Custom theme color badge dot indicator
            if (node.themeColor != null) {
                themeBadgePaint.color = Color.WHITE
                themeBadgePaint.alpha = (255 * alphaMultiplier).toInt()
                canvas.drawCircle(node.x + radius * 0.65f, node.y - radius * 0.65f, 7f, themeBadgePaint)
                themeBadgePaint.color = node.themeColor!!
                canvas.drawCircle(node.x + radius * 0.65f, node.y - radius * 0.65f, 5.5f, themeBadgePaint)
            }

            // Node Text Label
            val displayName = if (node.fileNode.name.length > 14) {
                node.fileNode.name.take(12) + ".."
            } else {
                node.fileNode.name
            }
            textPaint.alpha = (255 * alphaMultiplier).toInt()
            canvas.drawText(displayName, node.x, node.y + radius + 28f, textPaint)

            // Subtext (Size or Category)
            val subText = if (node.nodeType == CanvasNodeType.DIRECTORY) "DIR" else node.fileNode.formattedSize
            subTextPaint.alpha = (200 * alphaMultiplier).toInt()
            canvas.drawText(subText, node.x, node.y + radius + 50f, subTextPaint)
        }
    }

    private fun drawMiniMap(canvas: Canvas) {
        if (nodes.isEmpty()) return

        val mapWidth = 135f
        val mapHeight = 100f
        val padding = 20f

        miniMapBounds.set(
            width - mapWidth - padding,
            height - mapHeight - padding,
            width - padding,
            height - padding
        )

        // Semi-transparent frosted card
        canvas.drawRoundRect(miniMapBounds, 14f, 14f, miniMapBgPaint)
        canvas.drawRoundRect(miniMapBounds, 14f, 14f, miniMapBorderPaint)
        canvas.drawText("CANVAS", miniMapBounds.left + 8f, miniMapBounds.top + 16f, miniMapTitlePaint)

        // Compute world bounding box
        var minX = -600f
        var maxX = 600f
        var minY = -600f
        var maxY = 600f

        for (n in nodes) {
            minX = min(minX, n.x - 80f)
            maxX = max(maxX, n.x + 80f)
            minY = min(minY, n.y - 80f)
            maxY = max(maxY, n.y + 80f)
        }

        val worldW = max(maxX - minX, 1000f)
        val worldH = max(maxY - minY, 1000f)
        val scaleX = (mapWidth - 14f) / worldW
        val scaleY = (mapHeight - 20f) / worldH
        val mapScale = min(scaleX, scaleY)

        val mapCenterX = miniMapBounds.centerX()
        val mapCenterY = miniMapBounds.centerY() + 4f
        val worldCenterX = (minX + maxX) / 2f
        val worldCenterY = (minY + maxY) / 2f

        // Draw nodes on mini-map
        for (n in nodes) {
            val px = mapCenterX + (n.x - worldCenterX) * mapScale
            val py = mapCenterY + (n.y - worldCenterY) * mapScale

            miniMapNodePaint.color = if (n.isSelected) Color.parseColor("#38BDF8") else n.color
            canvas.drawCircle(px, py, 2.5f, miniMapNodePaint)
        }

        // Draw camera viewport frustum rectangle on mini-map
        val camWorldLeft = viewport.toWorldX(0f)
        val camWorldTop = viewport.toWorldY(0f)
        val camWorldRight = viewport.toWorldX(width.toFloat())
        val camWorldBottom = viewport.toWorldY(height.toFloat())

        val frustumLeft = mapCenterX + (camWorldLeft - worldCenterX) * mapScale
        val frustumTop = mapCenterY + (camWorldTop - worldCenterY) * mapScale
        val frustumRight = mapCenterX + (camWorldRight - worldCenterX) * mapScale
        val frustumBottom = mapCenterY + (camWorldBottom - worldCenterY) * mapScale

        tempBounds.set(
            max(frustumLeft, miniMapBounds.left),
            max(frustumTop, miniMapBounds.top),
            min(frustumRight, miniMapBounds.right),
            min(frustumBottom, miniMapBounds.bottom)
        )
        canvas.drawRect(tempBounds, miniMapViewfrustumPaint)
    }

    /**
     * Precision manual zoom scale adjustment with screen center pinning
     */
    fun setManualScale(newScale: Float) {
        val clampedScale = newScale.coerceIn(0.20f, 4.0f)
        val centerX = width / 2f
        val centerY = height / 2f
        val scaleDelta = clampedScale / viewport.scale
        viewport.scale = clampedScale
        viewport.translationX = centerX - (centerX - viewport.translationX) * scaleDelta
        viewport.translationY = centerY - (centerY - viewport.translationY) * scaleDelta
        invalidate()
    }

    /**
     * Toggles the visual Activity Heatmap layer
     */
    fun toggleHeatmap(): Boolean {
        isHeatmapVisible = !isHeatmapVisible
        if (isHeatmapVisible) {
            heatScores = CanvasHeatmapEvaluator.computeHeatScores(nodes)
        }
        onHeatmapToggledListener?.invoke(isHeatmapVisible)
        invalidate()
        return isHeatmapVisible
    }

    fun updateHeatmapScores() {
        if (isHeatmapVisible) {
            heatScores = CanvasHeatmapEvaluator.computeHeatScores(nodes)
            invalidate()
        }
    }

    /**
     * Exports the current canvas arrangement and layout to a high-resolution Bitmap.
     */
    fun exportLayoutAsBitmap(): Bitmap {
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 1920
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val exportCanvas = Canvas(bitmap)

        // Fill modern dark canvas background
        exportCanvas.drawColor(Color.parseColor("#0F172A"))

        // Render current view state
        draw(exportCanvas)
        return bitmap
    }
}
