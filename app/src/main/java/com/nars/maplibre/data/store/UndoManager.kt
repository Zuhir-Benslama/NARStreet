package com.nars.maplibre.data.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UndoManager {
    companion object {
        private const val MAX_UNDO_SIZE = 50
    }

    private val lock = Any()
    private val undoStack = ArrayDeque<UndoAction>()
    private val _canUndoState = MutableStateFlow(false)

    /** Observable undo availability — drives UI controls (e.g. undo button). */
    val canUndoState: StateFlow<Boolean> = _canUndoState.asStateFlow()

    val canUndo: Boolean get() = synchronized(lock) { undoStack.isNotEmpty() }

    private fun syncState() {
        _canUndoState.value = synchronized(lock) { undoStack.isNotEmpty() }
    }

    fun addUndoAction(action: UndoAction) = synchronized(lock) {
        // Consecutive updates of the same feature (a drag emits one ChangeEnd per
        // sub-move) are collapsed so a single undo reverts the whole gesture:
        // keep the earliest oldFeature, supersede the latest newFeature.
        val top = undoStack.lastOrNull()
        val mergedAction =
            if (top is UndoAction.Update && action is UndoAction.Update &&
                top.newFeature.id == action.newFeature.id
            ) {
                undoStack.removeLast()
                top.copy(newFeature = action.newFeature)
            } else {
                action
            }
        undoStack.addLast(mergedAction)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeFirst()
        }
        syncState()
    }

    fun popUndoAction(): UndoAction? = synchronized(lock) {
        if (undoStack.isEmpty()) return@synchronized null
        val action = undoStack.removeLast()
        syncState()
        action
    }

    /** Drops all recorded actions (used when the session's feature state is cleared). */
    fun clear() = synchronized(lock) {
        undoStack.clear()
        syncState()
    }

    /**
     * Removes the most recent [UndoAction.Delete] referencing a feature from the
     * stack. Used when an optimistic delete fails and the feature is restored
     * locally: the Delete action recorded at delete time would otherwise try to
     * restore a feature that is still present. Update/Create actions for the same
     * feature are left untouched.
     */
    fun removeMostRecentActionForFeature(featureId: String): UndoAction? = synchronized(lock) {
        val index = undoStack.indexOfLast { it is UndoAction.Delete && it.references(featureId) }
        if (index == -1) return@synchronized null
        val action = undoStack.removeAt(index)
        syncState()
        action
    }
}
