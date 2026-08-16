package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("TooManyFunctions")
class FeatureStore : FeatureStoreInterface {
    val undoManager = UndoManager(this)
    private val lock = ReentrantLock()

    private val _featuresByPhase = MutableStateFlow<Map<String, List<NarsFeature>>>(emptyMap())
    override val featuresByPhase: StateFlow<Map<String, List<NarsFeature>>> = _featuresByPhase.asStateFlow()

    private val _allFeatures = MutableStateFlow<List<NarsFeature>>(emptyList())
    override val allFeatures: StateFlow<List<NarsFeature>> = _allFeatures.asStateFlow()

    private val _selectedFeature = MutableStateFlow<NarsFeature?>(null)
    override val selectedFeature: StateFlow<NarsFeature?> = _selectedFeature.asStateFlow()

    private val _currentPhase = MutableStateFlow<PhaseDefinition?>(null)
    override val currentPhase: StateFlow<PhaseDefinition?> = _currentPhase.asStateFlow()

    private val _referenceRoadDbId = MutableStateFlow<String?>(null)
    override val referenceRoadDbId: StateFlow<String?> = _referenceRoadDbId.asStateFlow()

    private inline fun withPhaseMap(block: (MutableMap<String, List<NarsFeature>>) -> Unit) {
        val currentMap = _featuresByPhase.value.toMutableMap()
        block(currentMap)
        _featuresByPhase.value = currentMap
    }

    private fun rebuildPhaseMap(features: List<NarsFeature>) {
        _featuresByPhase.value = features.groupBy { it.properties.phase }
    }

    init {
        _currentPhase.value = Phases.ALL.first()
    }

    override fun setCurrentPhase(phase: PhaseDefinition) = lock.withLock {
        _currentPhase.value = phase
    }

    override fun setCurrentPhaseByKey(key: String) {
        Phases.getByKey(key)?.let { setCurrentPhase(it) }
    }

    override fun addFeature(feature: NarsFeature, recordUndo: Boolean) = lock.withLock {
        val all = _allFeatures.value
        val isDuplicate = all.any { it.id == feature.id || (feature.dbId != null && it.dbId == feature.dbId) }
        if (isDuplicate) return@withLock
        withPhaseMap { map ->
            map[feature.properties.phase] = map.getOrDefault(feature.properties.phase, emptyList()) + feature
        }
        _allFeatures.value = all + feature

        if (recordUndo) {
            // Lock ordering: store lock -> undo lock. UndoManager.executeUndo()
            // releases the undo lock BEFORE calling back into the store, so this
            // nesting can never deadlock — preserve that order if either side
            // changes.
            undoManager.addUndoAction(UndoAction.Create(feature))
        }
    }

    override fun addFeatures(features: List<NarsFeature>) = lock.withLock {
        val existing = _allFeatures.value

        // Upsert: existing entries are replaced in place by an incoming feature
        // with the same id/dbId (so a reload picks up server-side changes), and
        // genuinely new incoming features are appended.
        val merged =
            existing.map { existingFeature ->
                features.firstOrNull { incoming ->
                    incoming.id == existingFeature.id ||
                        (incoming.dbId != null && incoming.dbId == existingFeature.dbId)
                } ?: existingFeature
            } + features.filter { incoming ->
                existing.none { existingFeature ->
                    existingFeature.id == incoming.id ||
                        (incoming.dbId != null && incoming.dbId == existingFeature.dbId)
                }
            }
        rebuildPhaseMap(merged)
        _allFeatures.value = merged
    }

    override fun updateFeature(featureId: String, updatedFeature: NarsFeature) = lock.withLock {
        _allFeatures.value = _allFeatures.value.map { if (it.id == featureId) updatedFeature else it }
        rebuildPhaseMap(_allFeatures.value)
        if (_selectedFeature.value?.id == featureId) {
            _selectedFeature.value = updatedFeature
        }
    }

    override fun removeFeature(featureId: String) = lock.withLock {
        withPhaseMap { map ->
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val filtered = entry.value.filter { it.id != featureId }
                if (filtered.isEmpty()) {
                    iterator.remove()
                } else {
                    map[entry.key] = filtered
                }
            }
        }
        _allFeatures.value = _allFeatures.value.filter { it.id != featureId }

        if (_selectedFeature.value?.id == featureId) {
            _selectedFeature.value = null
        }
    }

    override fun getFeaturesByPhase(phaseKey: String): List<NarsFeature> =
        _featuresByPhase.value[phaseKey] ?: emptyList()

    override fun getCurrentPhaseFeatures(): List<NarsFeature> =
        currentPhase.value?.let { getFeaturesByPhase(it.key) } ?: emptyList()

    override fun getFeatureById(featureId: String): NarsFeature? = _allFeatures.value.find { it.id == featureId }

    override fun selectFeature(feature: NarsFeature?) {
        _selectedFeature.value = feature
    }

    override fun clearAll() = lock.withLock {
        _featuresByPhase.value = emptyMap()
        _allFeatures.value = emptyList()
        _selectedFeature.value = null
        _currentPhase.value = Phases.ALL.first()
        _referenceRoadDbId.value = null
        undoManager.clear()
    }

    override fun clearPhase(phaseKey: String) = lock.withLock {
        withPhaseMap { it.remove(phaseKey) }
        _allFeatures.value = _allFeatures.value.filter { it.properties.phase != phaseKey }
    }

    override fun getFeatureCounts(): Map<String, Int> = _featuresByPhase.value.mapValues { it.value.size }

    override fun setReferenceRoad(dbId: String?) {
        _referenceRoadDbId.value = dbId
    }

    override fun getAllRoads(): List<NarsFeature> = _featuresByPhase.value[Phases.ROADS_KEY] ?: emptyList()

    override val canUndo: Boolean get() = undoManager.canUndo

    override val undoState: StateFlow<Boolean> = undoManager.canUndoState

    override fun executeUndo(): UndoAction? = undoManager.executeUndo()

    override fun addUndoAction(action: UndoAction) = undoManager.addUndoAction(action)

    override fun removeMostRecentActionForFeature(featureId: String): UndoAction? =
        undoManager.removeMostRecentActionForFeature(featureId)
}
