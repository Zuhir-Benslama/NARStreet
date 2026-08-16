package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.NarsFeature

/**
 * Undo action matching web version (undo.ts)
 */
sealed class UndoAction {
    data class Delete(val feature: NarsFeature) : UndoAction()

    data class Create(val feature: NarsFeature) : UndoAction()

    data class Update(val oldFeature: NarsFeature, val newFeature: NarsFeature) : UndoAction()

    fun references(featureId: String): Boolean = when (this) {
        is Delete -> feature.id == featureId
        is Create -> feature.id == featureId
        is Update -> newFeature.id == featureId || oldFeature.id == featureId
    }
}
