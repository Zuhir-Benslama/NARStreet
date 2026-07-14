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
    override val undoManager = UndoManager(this)
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

    init {
        _currentPhase.value = Phases.ALL.first()
    }

    override fun setCurrentPhase(phase: PhaseDefinition) {
        _currentPhase.value = phase
    }

    override fun setCurrentPhaseByKey(key: String) {
        Phases.getByKey(key)?.let { setCurrentPhase(it) }
    }

    override fun addFeature(feature: NarsFeature, recordUndo: Boolean) = lock.withLock {
        withPhaseMap { map ->
            map[feature.properties.phase] = map.getOrDefault(feature.properties.phase, emptyList()) + feature
        }
        _allFeatures.value = _allFeatures.value + feature

        if (recordUndo) {
            undoManager.addUndoAction(UndoAction.Create(feature, feature.properties.phase))
        }
    }

    override fun addFeatures(features: List<NarsFeature>) = lock.withLock {
        withPhaseMap { map ->
            features.forEach { feature ->
                map[feature.properties.phase] = map.getOrDefault(feature.properties.phase, emptyList()) + feature
            }
        }
        _allFeatures.value = _allFeatures.value + features
    }

    override fun updateFeature(featureId: String, updatedFeature: NarsFeature) = lock.withLock {
        withPhaseMap { map ->
            for (phase in map.keys) {
                map[phase] = map[phase].orEmpty().map { if (it.id == featureId) updatedFeature else it }
            }
        }
        _allFeatures.value = _allFeatures.value.map { if (it.id == featureId) updatedFeature else it }
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
}

data class FeatureCounts(
    val roads: Int = 0,
    val mainEntrances: Int = 0,
    val secondaryEntrances: Int = 0,
    val namingPanels: Int = 0,
)
