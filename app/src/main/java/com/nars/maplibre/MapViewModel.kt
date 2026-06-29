package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.store.FeatureStoreInterface
import com.nars.maplibre.data.store.UndoAction
import com.nars.maplibre.utils.NarsLogger
import com.nars.maplibre.utils.PhaseNavigationResult
import com.nars.maplibre.utils.PhaseNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(
    application: Application,
    val featureStore: FeatureStoreInterface,
    private val appPreferences: AppPreferences,
    private val apiService: ApiService,
) : AndroidViewModel(application) {
    private val phaseNavigator = PhaseNavigator(featureStore)

    val currentPhase: StateFlow<PhaseDefinition?> = featureStore.currentPhase
    val allFeatures: StateFlow<List<NarsFeature>> = featureStore.allFeatures
    private val _currentPhaseFeatures = MutableStateFlow<List<NarsFeature>>(emptyList())
    val currentPhaseFeatures: StateFlow<List<NarsFeature>> = _currentPhaseFeatures
    val selectedFeature: StateFlow<NarsFeature?> = featureStore.selectedFeature

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _baseLayer = MutableStateFlow(appPreferences.baseLayer)
    val baseLayer: StateFlow<BaseLayerType> = _baseLayer.asStateFlow()

    private val _drawingEnabled = MutableStateFlow(false)
    val drawingEnabled: StateFlow<Boolean> = _drawingEnabled.asStateFlow()

    private val _editModeEnabled = MutableStateFlow(false)
    val editModeEnabled: StateFlow<Boolean> = _editModeEnabled.asStateFlow()

    val referenceRoadDbId: StateFlow<String?> = featureStore.referenceRoadDbId
    val canUndo: Boolean get() = featureStore.undoManager.canUndo

    init {
        featureStore.setCurrentPhase(Phases.ALL.first())
        viewModelScope.launch {
            featureStore.currentPhase.collect { phase ->
                phase?.let {
                    _currentPhaseFeatures.value = featureStore.getFeaturesByPhase(it.key)
                }
            }
        }
    }

    fun setCurrentPhase(phase: PhaseDefinition): PhaseDefinition? {
        val currentIndex = featureStore.currentPhase.value?.index ?: 0
        if (phase.index > currentIndex) {
            val result = phaseNavigator.canAdvance(phase.index)
            if (result is PhaseNavigationResult.Blocked) {
                updateUiState(errorMessage = result.message)
                NarsLogger.d("MapViewModel", "Phase validation failed: ${result.message}")
                return null
            }
        }
        NarsLogger.d("MapViewModel", "Setting current phase to: ${phase.label} (${phase.key})")
        featureStore.setCurrentPhase(phase)
        appPreferences.currentPhase = phase.key
        _drawingEnabled.value = false
        _editModeEnabled.value = false
        return phase
    }

    fun goToNextPhase(): PhaseDefinition? {
        val nextPhase = phaseNavigator.goNext()
        if (nextPhase == null) {
            val currentIndex = featureStore.currentPhase.value?.index ?: 0
            val result = phaseNavigator.canAdvance(currentIndex + 1)
            if (result is PhaseNavigationResult.Blocked) updateUiState(errorMessage = result.message)
        }
        return nextPhase
    }

    fun goToPreviousPhase(): PhaseDefinition? = phaseNavigator.goBack()

    fun canGoNextPhase(): Boolean = phaseNavigator.canGoForward()

    fun setReferenceRoad(dbId: String?) = featureStore.setReferenceRoad(dbId)

    fun undo(): Boolean {
        val action = featureStore.undoManager.executeUndo()
        val app = getApplication<Application>()
        if (action == null) {
            updateUiState(errorMessage = app.getString(R.string.map_nothing_undo))
            return false
        }
        when (action) {
            is UndoAction.Delete -> {
                val msg = app.getString(R.string.undo_restored_format, action.feature.properties.name)
                updateUiState(successMessage = msg)
            }

            is UndoAction.Create -> {
                val msg = app.getString(R.string.undo_removed_format, action.feature.properties.name)
                updateUiState(successMessage = msg)
            }

            is UndoAction.Update -> {
                val msg = app.getString(R.string.undo_restored_format, action.oldFeature.properties.name)
                updateUiState(successMessage = msg)
            }
        }
        return true
    }

    fun addFeature(feature: NarsFeature) = featureStore.addFeature(feature, recordUndo = true)

    fun updateFeature(feature: NarsFeature) {
        val oldFeature = featureStore.getFeatureById(feature.id)
        featureStore.updateFeature(feature.id, feature)
        oldFeature?.let {
            featureStore.undoManager.addUndoAction(
                UndoAction.Update(
                    oldFeature = it,
                    newFeature = feature,
                    phaseKey = feature.properties.phase,
                ),
            )
        }
    }

    fun deleteFeature(featureId: String) {
        val feature = featureStore.getFeatureById(featureId)
        if (feature != null) {
            featureStore.undoManager.addUndoAction(
                UndoAction.Delete(
                    feature = feature,
                    phaseKey = feature.properties.phase,
                ),
            )
        }
        featureStore.removeFeature(featureId)
    }

    val selectedFeatureId: StateFlow<String?> =
        featureStore.selectedFeature
            .map { it?.id }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun selectFeature(feature: NarsFeature?) = featureStore.selectFeature(feature)

    fun toggleDrawing(enabled: Boolean) {
        _drawingEnabled.value = enabled
        if (enabled) _editModeEnabled.value = false
    }

    fun toggleEditMode(enabled: Boolean) {
        _editModeEnabled.value = enabled
        if (enabled) _drawingEnabled.value = false
    }

    fun setBaseLayer(layer: BaseLayerType) {
        _baseLayer.value = layer
        appPreferences.baseLayer = layer
    }

    fun clearSelection() = featureStore.selectFeature(null)

    fun updateUiState(isLoading: Boolean? = null, errorMessage: String? = null, successMessage: String? = null) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = isLoading ?: _uiState.value.isLoading,
                errorMessage = errorMessage,
                successMessage = successMessage,
            )
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showSettings: Boolean = false,
    val showFeatureModal: Boolean = false,
)
