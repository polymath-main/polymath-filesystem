package com.polymath.fs.viewmodels

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polymath.fs.core.canvas.physics.ForceSimulationEngine
import com.polymath.fs.data.db.AppDatabase
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import com.polymath.fs.data.repository.CognitiveCanvasRepository
import com.polymath.fs.domain.canvas.models.*
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
        CognitiveCanvasRepository(db.cognitiveCanvasDao())
    }

    private val _uiState = MutableStateFlow(CognitiveCanvasUiState())
    val uiState: StateFlow<CognitiveCanvasUiState> = _uiState.asStateFlow()

    private val physicsEngine = ForceSimulationEngine()
    val actionStack = CanvasActionStack()

    init {
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
        }
        onComplete(action)
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
