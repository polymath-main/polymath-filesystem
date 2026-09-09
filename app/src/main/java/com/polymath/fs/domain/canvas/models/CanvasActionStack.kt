package com.polymath.fs.domain.canvas.models

sealed class CanvasAction(val description: String) {
    data class MoveNode(
        val nodeId: String,
        val filePath: String,
        val oldX: Float,
        val oldY: Float,
        val newX: Float,
        val newY: Float
    ) : CanvasAction("Move node")

    data class UpdateThemeColor(
        val nodeId: String,
        val filePath: String,
        val oldColor: Int?,
        val newColor: Int?
    ) : CanvasAction("Update theme color")

    data class LinkNodes(
        val edgeId: String,
        val sourceNodeId: String,
        val targetNodeId: String,
        val relationType: CanvasRelationType,
        val label: String
    ) : CanvasAction("Link nodes")
}

class CanvasActionStack(private val maxCapacity: Int = 50) {
    private val undoList = mutableListOf<CanvasAction>()
    private val redoList = mutableListOf<CanvasAction>()

    var onStackChangedListener: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    fun pushAction(action: CanvasAction) {
        undoList.add(action)
        if (undoList.size > maxCapacity) {
            undoList.removeAt(0)
        }
        redoList.clear()
        notifyChange()
    }

    fun canUndo(): Boolean = undoList.isNotEmpty()
    fun canRedo(): Boolean = redoList.isNotEmpty()

    fun popUndo(): CanvasAction? {
        if (undoList.isEmpty()) return null
        val action = undoList.removeAt(undoList.size - 1)
        redoList.add(action)
        notifyChange()
        return action
    }

    fun popRedo(): CanvasAction? {
        if (redoList.isEmpty()) return null
        val action = redoList.removeAt(redoList.size - 1)
        undoList.add(action)
        notifyChange()
        return action
    }

    fun clear() {
        undoList.clear()
        redoList.clear()
        notifyChange()
    }

    private fun notifyChange() {
        onStackChangedListener?.invoke(canUndo(), canRedo())
    }
}
