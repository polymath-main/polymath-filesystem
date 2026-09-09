package com.polymath.fs.viewmodels

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polymath.fs.core.canvas.physics.ForceSimulationEngine
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity
import com.polymath.fs.data.repository.CognitiveCanvasRepository
import com.polymath.fs.domain.canvas.ai.CanvasMLSuggestionEngine
import com.polymath.fs.domain.canvas.ai.CanvasSuggestionCluster
import com.polymath.fs.domain.canvas.models.*
import com.polymath.fs.domain.canvas.presets.PresetLayoutEngine
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CognitiveCanvasUiState(
    val canvas: CognitiveCanvas = CognitiveCanvas(),
    val selectedNodes: List<CanvasNode> = emptyList(),
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val nodeCount: Int = 0,
    val edgeCount: Int = 0,
    val isSnapToGridEnabled: Boolean = true,
    val isVisualGridOverlayVisible: Boolean = true,
    val isLinkModeActive: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null
)

class CognitiveCanvasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CognitiveCanvasRepository by lazy {
        val db = AppDatabase.getDatabase(application)
        CognitiveCanvasRepository(
            canvasDao = db.cognitiveCanvasDao(),
            snapshotDao = db.workspaceSnapshotDao(),
            presetDao = db.canvasPresetDao()
        )
    }

    private val _uiState = MutableStateFlow(CognitiveCanvasUiState())
    val uiState: StateFlow<CognitiveCanvasUiState> = _uiState.asStateFlow()

    private val physicsEngine = ForceSimulationEngine()
    val actionStack = CanvasActionStack()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedBuiltInPresetsIfEmpty()
        }
        actionStack.onStackChangedListener = { canUndo, canRedo ->
            _uiState.value = _uiState.value.copy(
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
    }

    fun loadPath(path: String = Environment.getExternalStorageDirectory().absolutePath) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, currentPath = path)

            val dir = File(path)
            val files = dir.listFiles()?.toList() ?: emptyList()

            // Fetch persisted nodes and edges from Room
            val savedNodeEntities = repository.getNodesOnce(path).associateBy { it.filePath }
            val savedEdgeEntities = repository.getEdgesOnce(path)

            val canvas = CognitiveCanvas(
                name = dir.name.ifEmpty { "Root" },
                rootPath = dir.absolutePath
            )

            // Root central directory node
            val rootFileNode = FileNode.LocalFile(
                name = dir.name.ifEmpty { "Root" },
                path = dir.absolutePath,
                size = 0L,
                lastModified = dir.lastModified(),
                isDirectory = true
            )

            val rootSaved = savedNodeEntities[dir.absolutePath]
            val rootNode = CanvasNode(
                id = "root_${dir.absolutePath}",
                fileNode = rootFileNode,
                x = rootSaved?.x ?: 0f,
                y = rootSaved?.y ?: 0f,
                nodeType = CanvasNodeType.DIRECTORY,
                isPinned = rootSaved?.isPinned ?: true,
                themeColor = rootSaved?.themeColor
            )
            canvas.nodes.add(rootNode)

            val nodesToPersist = mutableListOf<CanvasNodeEntity>()
            if (rootSaved == null) {
                nodesToPersist.add(
                    CanvasNodeEntity(
                        filePath = dir.absolutePath,
                        canvasId = path,
                        x = rootNode.x,
                        y = rootNode.y,
                        isPinned = true,
                        themeColor = null
                    )
                )
            }

            // Child files & directories
            files.take(64).forEachIndexed { index, file ->
                val childFile = FileNode.LocalFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                )

                val saved = savedNodeEntities[file.absolutePath]
                val (posX, posY, isPinned) = if (saved != null) {
                    Triple(saved.x, saved.y, saved.isPinned)
                } else {
                    val angle = index * 0.4
                    val initX = (kotlin.math.cos(angle) * 220).toFloat()
                    val initY = (kotlin.math.sin(angle) * 220).toFloat()
                    nodesToPersist.add(
                        CanvasNodeEntity(
                            filePath = file.absolutePath,
                            canvasId = path,
                            x = initX,
                            y = initY,
                            isPinned = false,
                            themeColor = null
                        )
                    )
                    Triple(initX, initY, false)
                }

                val childNode = CanvasNode(
                    id = file.absolutePath,
                    fileNode = childFile,
                    x = posX,
                    y = posY,
                    isPinned = isPinned,
                    themeColor = saved?.themeColor
                )
                canvas.nodes.add(childNode)

                // Edge connecting parent to child
                canvas.edges.add(
                    CanvasEdge(
                        sourceNodeId = rootNode.id,
                        targetNodeId = childNode.id,
                        relationType = CanvasRelationType.PARENT_CHILD
                    )
                )
            }

            // Restore custom user relationships or cluster edges
            if (savedEdgeEntities.isNotEmpty()) {
                for (edgeEntity in savedEdgeEntities) {
                    canvas.edges.add(
                        CanvasEdge(
                            id = edgeEntity.id,
                            sourceNodeId = edgeEntity.sourcePath,
                            targetNodeId = edgeEntity.targetPath,
                            relationType = try {
                                CanvasRelationType.valueOf(edgeEntity.relationType)
                            } catch (e: Exception) {
                                CanvasRelationType.USER_LINK
                            },
                            label = edgeEntity.label,
                            weight = edgeEntity.weight
                        )
                    )
                }
            } else {
                // Generate similarity cluster edges for files sharing same extension
                val nonDirNodes: List<CanvasNode> = canvas.nodes.filter { !it.fileNode.isDirectory }
                val extensionGroups: Map<String, List<CanvasNode>> = nonDirNodes.groupBy { it.fileNode.extension }
                val edgesToPersist = mutableListOf<CanvasEdgeEntity>()
                for ((ext, group) in extensionGroups) {
                    if (group.size > 1 && ext.isNotEmpty()) {
                        for (i in 0 until group.size - 1) {
                            val edge = CanvasEdge(
                                sourceNodeId = group[i].id,
                                targetNodeId = group[i + 1].id,
                                relationType = CanvasRelationType.SIMILARITY,
                                label = ext.uppercase(),
                                weight = 0.5f
                            )
                            canvas.edges.add(edge)
                            edgesToPersist.add(
                                CanvasEdgeEntity(
                                    id = edge.id,
                                    canvasId = path,
                                    sourcePath = edge.sourceNodeId,
                                    targetPath = edge.targetNodeId,
                                    relationType = edge.relationType.name,
                                    label = edge.label,
                                    weight = edge.weight
                                )
                            )
                        }
                    }
                }
                if (edgesToPersist.isNotEmpty()) {
                    repository.saveEdges(edgesToPersist)
                }
            }

            if (nodesToPersist.isNotEmpty()) {
                repository.saveNodes(nodesToPersist)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    canvas = canvas,
                    isLoading = false,
                    currentPath = path,
                    nodeCount = canvas.nodes.size,
                    edgeCount = canvas.edges.size
                )
            }
        }
    }

    fun recordNodeMove(node: CanvasNode, oldX: Float, oldY: Float) {
        if (oldX == node.x && oldY == node.y) return
        actionStack.pushAction(
            CanvasAction.MoveNode(
                nodeId = node.id,
                filePath = node.fileNode.path,
                oldX = oldX,
                oldY = oldY,
                newX = node.x,
                newY = node.y
            )
        )
    }

    fun persistNodePosition(node: CanvasNode, canvasId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNode(
                CanvasNodeEntity(
                    filePath = node.fileNode.path,
                    canvasId = canvasId,
                    x = node.x,
                    y = node.y,
                    isPinned = node.isPinned,
                    themeColor = node.themeColor
                )
            )
        }
    }

    fun updateNodeThemeColor(node: CanvasNode, color: Int?, canvasId: String) {
        val previousColor = node.themeColor
        node.themeColor = color
        actionStack.pushAction(
            CanvasAction.UpdateThemeColor(
                nodeId = node.id,
                filePath = node.fileNode.path,
                oldColor = previousColor,
                newColor = color
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getNodeByPath(node.fileNode.path)
            if (existing != null) {
                repository.saveNode(existing.copy(themeColor = color, updatedAt = System.currentTimeMillis()))
            } else {
                repository.saveNode(
                    CanvasNodeEntity(
                        filePath = node.fileNode.path,
                        canvasId = canvasId,
                        x = node.x,
                        y = node.y,
                        isPinned = node.isPinned,
                        themeColor = color
                    )
                )
            }
        }
    }

    fun persistAllNodePositions() {
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val entities = currentCanvas.nodes.map {
                CanvasNodeEntity(
                    filePath = it.fileNode.path,
                    canvasId = canvasId,
                    x = it.x,
                    y = it.y,
                    isPinned = it.isPinned,
                    themeColor = it.themeColor
                )
            }
            repository.saveNodes(entities)
        }
    }

    fun linkNodes(
        sourceNode: CanvasNode,
        targetNode: CanvasNode,
        relationType: CanvasRelationType = CanvasRelationType.USER_LINK,
        label: String = "Link"
    ) {
        val canvasId = _uiState.value.currentPath
        val edge = _uiState.value.canvas.linkNodes(
            sourceId = sourceNode.id,
            targetId = targetNode.id,
            relationType = relationType,
            label = label
        )

        actionStack.pushAction(
            CanvasAction.LinkNodes(
                edgeId = edge.id,
                sourceNodeId = sourceNode.id,
                targetNodeId = targetNode.id,
                relationType = relationType,
                label = label
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveEdge(
                CanvasEdgeEntity(
                    id = edge.id,
                    canvasId = canvasId,
                    sourcePath = edge.sourceNodeId,
                    targetPath = edge.targetNodeId,
                    relationType = edge.relationType.name,
                    label = edge.label,
                    weight = edge.weight
                )
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    edgeCount = _uiState.value.canvas.edges.size
                )
            }
        }
    }

    fun toggleSnapToGrid() {
        _uiState.value = _uiState.value.copy(
            isSnapToGridEnabled = !_uiState.value.isSnapToGridEnabled
        )
    }

    fun toggleVisualGridOverlay() {
        _uiState.value = _uiState.value.copy(
            isVisualGridOverlayVisible = !_uiState.value.isVisualGridOverlayVisible
        )
    }

    fun undoAction(onComplete: (CanvasAction?) -> Unit) {
        val action = actionStack.popUndo() ?: return
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath

        when (action) {
            is CanvasAction.MoveNode -> {
                val node = currentCanvas.findNodeById(action.nodeId)
                if (node != null) {
                    node.x = action.oldX
                    node.y = action.oldY
                    persistNodePosition(node, canvasId)
                }
            }
            is CanvasAction.UpdateThemeColor -> {
                val node = currentCanvas.findNodeById(action.nodeId)
                if (node != null) {
                    node.themeColor = action.oldColor
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.updateThemeColor(node.fileNode.path, action.oldColor)
                    }
                }
            }
            is CanvasAction.LinkNodes -> {
                currentCanvas.edges.removeAll { it.id == action.edgeId }
                viewModelScope.launch(Dispatchers.IO) {
                    repository.deleteEdge(action.edgeId)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(edgeCount = currentCanvas.edges.size)
                    }
                }
            }
            is CanvasAction.RestoreSnapshot -> {
                restoreSnapshotInternal(action.previousSnapshot)
            }
        }
        onComplete(action)
    }

    fun redoAction(onComplete: (CanvasAction?) -> Unit) {
        val action = actionStack.popRedo() ?: return
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath

        when (action) {
            is CanvasAction.MoveNode -> {
                val node = currentCanvas.findNodeById(action.nodeId)
                if (node != null) {
                    node.x = action.newX
                    node.y = action.newY
                    persistNodePosition(node, canvasId)
                }
            }
            is CanvasAction.UpdateThemeColor -> {
                val node = currentCanvas.findNodeById(action.nodeId)
                if (node != null) {
                    node.themeColor = action.newColor
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.updateThemeColor(node.fileNode.path, action.newColor)
                    }
                }
            }
            is CanvasAction.LinkNodes -> {
                val edge = CanvasEdge(
                    id = action.edgeId,
                    sourceNodeId = action.sourceNodeId,
                    targetNodeId = action.targetNodeId,
                    relationType = action.relationType,
                    label = action.label
                )
                currentCanvas.edges.add(edge)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveEdge(
                        CanvasEdgeEntity(
                            id = edge.id,
                            canvasId = canvasId,
                            sourcePath = edge.sourceNodeId,
                            targetPath = edge.targetNodeId,
                            relationType = edge.relationType.name,
                            label = edge.label,
                            weight = edge.weight
                        )
                    )
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(edgeCount = currentCanvas.edges.size)
                    }
                }
            }
            is CanvasAction.RestoreSnapshot -> {
                restoreSnapshotInternal(action.restoredSnapshot)
            }
        }
        onComplete(action)
    }

    private fun restoreSnapshotInternal(snapshot: WorkspaceSnapshot) {
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath

        val nodeMap = currentCanvas.nodes.associateBy { it.fileNode.path }
        val nodesToPersist = mutableListOf<CanvasNodeEntity>()

        for (nData in snapshot.nodes) {
            val node = nodeMap[nData.filePath]
            if (node != null) {
                node.x = nData.x
                node.y = nData.y
                node.isPinned = nData.isPinned
                node.themeColor = nData.themeColor
                node.vx = 0f
                node.vy = 0f

                nodesToPersist.add(
                    CanvasNodeEntity(
                        filePath = node.fileNode.path,
                        canvasId = canvasId,
                        x = node.x,
                        y = node.y,
                        isPinned = node.isPinned,
                        themeColor = node.themeColor
                    )
                )
            }
        }

        currentCanvas.edges.clear()
        val edgesToPersist = mutableListOf<CanvasEdgeEntity>()
        for (eData in snapshot.edges) {
            val edge = CanvasEdge(
                id = eData.id,
                sourceNodeId = eData.sourcePath,
                targetNodeId = eData.targetPath,
                relationType = try { CanvasRelationType.valueOf(eData.relationType) } catch (e: Exception) { CanvasRelationType.USER_LINK },
                label = eData.label,
                weight = eData.weight
            )
            currentCanvas.edges.add(edge)
            edgesToPersist.add(
                CanvasEdgeEntity(
                    id = edge.id,
                    canvasId = canvasId,
                    sourcePath = edge.sourceNodeId,
                    targetPath = edge.targetNodeId,
                    relationType = edge.relationType.name,
                    label = edge.label,
                    weight = edge.weight
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCanvas(canvasId)
            repository.saveNodes(nodesToPersist)
            repository.saveEdges(edgesToPersist)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    nodeCount = currentCanvas.nodes.size,
                    edgeCount = currentCanvas.edges.size
                )
            }
        }
    }

    // --- Workspace Snapshot APIs ---

    fun captureWorkspaceSnapshot(label: String, onComplete: ((WorkspaceSnapshot) -> Unit)? = null) {
        val currentCanvas = _uiState.value.canvas
        if (currentCanvas.nodes.isEmpty()) return

        val snapshot = WorkspaceSnapshot.capture(currentCanvas, label)
        val entity = WorkspaceSnapshotEntity(
            id = snapshot.id,
            canvasPath = snapshot.canvasPath,
            timestamp = snapshot.timestamp,
            label = snapshot.label,
            nodeCount = snapshot.nodes.size,
            edgeCount = snapshot.edges.size,
            snapshotJson = snapshot.toJson()
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSnapshot(entity)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(snapshot)
            }
        }
    }

    fun getWorkspaceSnapshots(onResult: (List<WorkspaceSnapshotEntity>) -> Unit) {
        val canvasPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            val snapshots = repository.getSnapshotsOnce(canvasPath)
            withContext(Dispatchers.Main) {
                onResult(snapshots)
            }
        }
    }

    fun restoreWorkspaceSnapshot(snapshot: WorkspaceSnapshot, onComplete: () -> Unit) {
        val currentCanvas = _uiState.value.canvas
        val beforeSnapshot = WorkspaceSnapshot.capture(currentCanvas, "Pre-restore Snapshot")

        actionStack.pushAction(
            CanvasAction.RestoreSnapshot(
                previousSnapshot = beforeSnapshot,
                restoredSnapshot = snapshot
            )
        )

        restoreSnapshotInternal(snapshot)
        onComplete()
    }

    fun deleteWorkspaceSnapshot(snapshotId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSnapshot(snapshotId)
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    // --- Machine Learning Auto-Grouping APIs ---

    fun computeMLGroupingSuggestions(onResult: (List<CanvasSuggestionCluster>) -> Unit) {
        val currentCanvas = _uiState.value.canvas
        viewModelScope.launch(Dispatchers.Default) {
            val suggestions = CanvasMLSuggestionEngine.proposeAutoGrouping(
                allNodes = currentCanvas.nodes,
                allEdges = currentCanvas.edges
            )
            withContext(Dispatchers.Main) {
                onResult(suggestions)
            }
        }
    }

    fun applyMLGroupingSuggestion(cluster: CanvasSuggestionCluster, onComplete: () -> Unit) {
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath
        val preSnapshot = WorkspaceSnapshot.capture(currentCanvas, "Before Auto-Grouping ${cluster.title}")

        val clusterNodes = cluster.nodes.filter { node -> currentCanvas.nodes.any { it.id == node.id } }
        if (clusterNodes.size >= 2) {
            var avgX = 0f
            var avgY = 0f
            for (node in clusterNodes) {
                avgX += node.x
                avgY += node.y
            }
            avgX /= clusterNodes.size
            avgY /= clusterNodes.size

            val orbitRadius = 110f
            val angleStep = (2 * Math.PI / clusterNodes.size).toFloat()

            clusterNodes.forEachIndexed { index, node ->
                val angle = index * angleStep
                node.x = avgX + (kotlin.math.cos(angle.toDouble()) * orbitRadius).toFloat()
                node.y = avgY + (kotlin.math.sin(angle.toDouble()) * orbitRadius).toFloat()
                node.themeColor = cluster.suggestedThemeColor
                node.vx = 0f
                node.vy = 0f
            }

            val edgesToPersist = mutableListOf<CanvasEdgeEntity>()
            for (i in 0 until clusterNodes.size - 1) {
                val src = clusterNodes[i]
                val tgt = clusterNodes[i + 1]
                val existing = currentCanvas.edges.any {
                    (it.sourceNodeId == src.id && it.targetNodeId == tgt.id) ||
                    (it.sourceNodeId == tgt.id && it.targetNodeId == src.id)
                }
                if (!existing) {
                    val edge = CanvasEdge(
                        sourceNodeId = src.id,
                        targetNodeId = tgt.id,
                        relationType = CanvasRelationType.SIMILARITY,
                        label = "ML Group",
                        weight = cluster.confidence
                    )
                    currentCanvas.edges.add(edge)
                    edgesToPersist.add(
                        CanvasEdgeEntity(
                            id = edge.id,
                            canvasId = canvasId,
                            sourcePath = edge.sourceNodeId,
                            targetPath = edge.targetNodeId,
                            relationType = edge.relationType.name,
                            label = edge.label,
                            weight = edge.weight
                        )
                    )
                }
            }

            val postSnapshot = WorkspaceSnapshot.capture(currentCanvas, "Applied Auto-Grouping ${cluster.title}")
            actionStack.pushAction(
                CanvasAction.RestoreSnapshot(
                    previousSnapshot = preSnapshot,
                    restoredSnapshot = postSnapshot
                )
            )

            viewModelScope.launch(Dispatchers.IO) {
                persistAllNodePositions()
                if (edgesToPersist.isNotEmpty()) {
                    repository.saveEdges(edgesToPersist)
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        edgeCount = currentCanvas.edges.size
                    )
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }

    // --- Canvas Presets APIs ---

    fun getCanvasPresets(onResult: (List<CanvasPresetEntity>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val presets = repository.getPresetsOnce()
            withContext(Dispatchers.Main) {
                onResult(presets)
            }
        }
    }

    fun applyCanvasPreset(presetEntity: CanvasPresetEntity, onComplete: () -> Unit) {
        val currentCanvas = _uiState.value.canvas
        val canvasId = _uiState.value.currentPath
        val preSnapshot = WorkspaceSnapshot.capture(currentCanvas, "Before Preset ${presetEntity.name}")

        val layoutType = try {
            PresetLayoutType.valueOf(presetEntity.layoutType)
        } catch (e: Exception) {
            PresetLayoutType.RESOURCE_CLUSTERS
        }

        val domainPreset = CanvasPreset(
            id = presetEntity.id,
            name = presetEntity.name,
            description = presetEntity.description,
            isBuiltIn = presetEntity.isBuiltIn,
            layoutType = layoutType,
            templateJson = presetEntity.templateJson
        )

        PresetLayoutEngine.applyPreset(currentCanvas, domainPreset)

        val postSnapshot = WorkspaceSnapshot.capture(currentCanvas, "Applied Preset ${presetEntity.name}")
        actionStack.pushAction(
            CanvasAction.RestoreSnapshot(
                previousSnapshot = preSnapshot,
                restoredSnapshot = postSnapshot
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            persistAllNodePositions()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun saveCurrentLayoutAsPreset(name: String, description: String, onComplete: (Boolean) -> Unit) {
        val currentCanvas = _uiState.value.canvas
        if (currentCanvas.nodes.isEmpty()) {
            onComplete(false)
            return
        }

        val snapshot = WorkspaceSnapshot.capture(currentCanvas, name)
        val entity = CanvasPresetEntity(
            id = "custom_preset_${System.currentTimeMillis()}",
            name = name,
            description = description,
            isBuiltIn = false,
            layoutType = PresetLayoutType.CUSTOM.name,
            createdAt = System.currentTimeMillis(),
            templateJson = snapshot.toJson()
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.savePreset(entity)
            withContext(Dispatchers.Main) {
                onComplete(true)
            }
        }
    }

    fun toggleLinkMode() {
        _uiState.value = _uiState.value.copy(
            isLinkModeActive = !_uiState.value.isLinkModeActive
        )
    }

    fun toggleNodeSelection(node: CanvasNode) {
        val currentCanvas = _uiState.value.canvas
        val targetNode = currentCanvas.findNodeById(node.id) ?: return
        targetNode.isSelected = !targetNode.isSelected

        val selected = currentCanvas.nodes.filter { it.isSelected }
        _uiState.value = _uiState.value.copy(selectedNodes = selected)
    }

    fun deselectAll() {
        val currentCanvas = _uiState.value.canvas
        currentCanvas.nodes.forEach { it.isSelected = false }
        _uiState.value = _uiState.value.copy(selectedNodes = emptyList())
    }

    // --- In-place Canvas File Operations ---

    fun renameFile(node: CanvasNode, newName: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(node.fileNode.path)
            val parent = oldFile.parentFile ?: return@launch
            val newFile = File(parent, newName)

            if (newFile.exists()) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "File with name '$newName' already exists")
                }
                return@launch
            }

            val success = oldFile.renameTo(newFile)
            if (success) {
                // Update Room DB
                repository.deleteNode(oldFile.absolutePath)
                repository.saveNode(
                    CanvasNodeEntity(
                        filePath = newFile.absolutePath,
                        canvasId = _uiState.value.currentPath,
                        x = node.x,
                        y = node.y,
                        isPinned = node.isPinned
                    )
                )

                // Reload canvas
                loadPath(_uiState.value.currentPath)
                withContext(Dispatchers.Main) {
                    onComplete(true, "Renamed to $newName")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to rename file")
                }
            }
        }
    }

    fun deleteFile(node: CanvasNode, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(node.fileNode.path)
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()

            if (success) {
                repository.deleteNode(file.absolutePath)
                _uiState.value.canvas.nodes.removeAll { it.id == node.id }
                _uiState.value.canvas.edges.removeAll { it.sourceNodeId == node.id || it.targetNodeId == node.id }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        nodeCount = _uiState.value.canvas.nodes.size,
                        edgeCount = _uiState.value.canvas.edges.size
                    )
                    onComplete(true, "Deleted ${node.fileNode.name}")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Could not delete file")
                }
            }
        }
    }

    fun moveFile(node: CanvasNode, destinationDir: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val src = File(node.fileNode.path)
            val destDir = File(destinationDir)
            if (!destDir.exists()) destDir.mkdirs()

            val target = File(destDir, src.name)
            val success = src.renameTo(target)

            if (success) {
                repository.deleteNode(src.absolutePath)
                repository.saveNode(
                    CanvasNodeEntity(
                        filePath = target.absolutePath,
                        canvasId = destDir.absolutePath,
                        x = node.x,
                        y = node.y,
                        isPinned = false
                    )
                )

                loadPath(_uiState.value.currentPath)
                withContext(Dispatchers.Main) {
                    onComplete(true, "Moved to ${target.name}")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to move file")
                }
            }
        }
    }
}
