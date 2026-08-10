package com.nars.maplibre.data.store

class UndoManager(private val featureStore: FeatureStoreInterface) {
    companion object {
        private const val MAX_UNDO_SIZE = 50
    }

    private val lock = Any()
    private val undoStack = ArrayDeque<UndoAction>()
    val canUndo: Boolean get() = synchronized(lock) { undoStack.isNotEmpty() }

    fun addUndoAction(action: UndoAction) = synchronized(lock) {
        undoStack.addLast(action)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeFirst()
        }
    }

    fun popUndoAction(): UndoAction? = synchronized(lock) {
        if (undoStack.isEmpty()) return@synchronized null
        undoStack.removeLast()
    }

    /**
     * Removes the most recent undo action referencing a feature from the stack.
     * Used when an optimistic delete fails and the feature is restored locally:
     * the Delete action recorded at delete time would otherwise try to restore
     * a feature that is still present.
     */
    fun removeMostRecentActionForFeature(featureId: String): UndoAction? = synchronized(lock) {
        val index = undoStack.indexOfLast { it.references(featureId) }
        if (index == -1) return@synchronized null
        undoStack.removeAt(index)
    }

    fun executeUndo(): UndoAction? {
        val action = synchronized(lock) { popUndoAction() } ?: return null

        when (action) {
            is UndoAction.Delete -> {
                val feature = action.feature
                if (featureStore.getFeatureById(feature.id) == null) featureStore.addFeature(feature)
            }

            is UndoAction.Create -> {
                featureStore.removeFeature(action.feature.id)
            }

            is UndoAction.Update -> {
                featureStore.updateFeature(action.newFeature.id, action.oldFeature)
            }
        }

        return action
    }
}
