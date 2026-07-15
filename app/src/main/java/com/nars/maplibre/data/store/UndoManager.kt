package com.nars.maplibre.data.store

class UndoManager(private val featureStore: FeatureStoreInterface) {
    companion object {
        private const val MAX_UNDO_SIZE = 50
    }

    private val lock = Any()
    private val undoStack = mutableListOf<UndoAction>()
    val canUndo: Boolean get() = synchronized(lock) { undoStack.isNotEmpty() }

    fun addUndoAction(action: UndoAction) = synchronized(lock) {
        undoStack.add(action)
        if (undoStack.size > MAX_UNDO_SIZE) {
            undoStack.removeAt(0)
        }
    }

    fun popUndoAction(): UndoAction? = synchronized(lock) {
        if (undoStack.isEmpty()) return@synchronized null
        undoStack.removeAt(undoStack.lastIndex)
    }

    fun executeUndo(): UndoAction? {
        val action = popUndoAction() ?: return null

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
