package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for feature data storage and retrieval.
 * Implementations manage in-memory feature state, phase-based organization,
 * and undo history via [undoManager].
 */
interface FeatureStoreInterface {
    val undoManager: UndoManager
    val featuresByPhase: StateFlow<Map<String, List<NarsFeature>>>
    val allFeatures: StateFlow<List<NarsFeature>>
    val selectedFeature: StateFlow<NarsFeature?>
    val currentPhase: StateFlow<PhaseDefinition?>
    val referenceRoadDbId: StateFlow<String?>

    fun setCurrentPhase(phase: PhaseDefinition)

    fun setCurrentPhaseByKey(key: String)

    fun addFeature(feature: NarsFeature, recordUndo: Boolean = false)

    fun addFeatures(features: List<NarsFeature>)

    fun updateFeature(featureId: String, updatedFeature: NarsFeature)

    fun removeFeature(featureId: String)

    fun getFeaturesByPhase(phaseKey: String): List<NarsFeature>

    fun getCurrentPhaseFeatures(): List<NarsFeature>

    fun getFeatureById(featureId: String): NarsFeature?

    fun selectFeature(feature: NarsFeature?)

    fun clearAll()

    fun clearPhase(phaseKey: String)

    fun getFeatureCounts(): Map<String, Int>

    fun setReferenceRoad(dbId: String?)

    fun getAllRoads(): List<NarsFeature>
}
