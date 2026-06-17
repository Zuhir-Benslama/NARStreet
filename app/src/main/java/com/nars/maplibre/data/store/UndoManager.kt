package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.utils.NarsLogger
import java.util.Collections

class UndoManager(private val featureStore: FeatureStore) {
    companion object {
        private const val MAX_UNDO_SIZE = 50
    }

    private val _undoStack = Collections.synchronizedList(mutableListOf<UndoAction>())
    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    fun addUndoAction(action: UndoAction) {
        _undoStack.add(action)
        if (_undoStack.size > MAX_UNDO_SIZE) {
            _undoStack.removeAt(0)
        }
    }

    fun popUndoAction(): UndoAction? {
        if (_undoStack.isEmpty()) return null
        return _undoStack.removeAt(_undoStack.lastIndex)
    }

    fun executeUndo(): UndoAction? {
        val action = popUndoAction() ?: return null

        when (action) {
            is UndoAction.Delete -> {
                val feature = action.feature
                if (featureStore.getFeatureById(feature.id) == null) featureStore.addFeature(feature)
                val roadDbId = when (val props = feature.properties) {
                    is com.nars.maplibre.data.model.FeatureProperties.HouseEntranceProperties -> props.roadDbId
                    else -> null
                }
                if (roadDbId != null) {
                    NarsLogger.d("UndoManager", "Cross-reference restored: entrance for road")
                }
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
