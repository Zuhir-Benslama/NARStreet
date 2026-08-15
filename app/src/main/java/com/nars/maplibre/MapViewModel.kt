package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
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
import kotlinx.coroutines.flow.update

@Suppress("TooManyFunctions")
class MapViewModel(
    application: Application,
    private val featureStore: FeatureStoreInterface,
    private val appPreferences: AppPreferences,
) : AndroidViewModel(application) {
    private val phaseNavigator = PhaseNavigator(featureStore)

    val currentPhase: StateFlow<PhaseDefinition?> = featureStore.currentPhase
    val allFeatures: StateFlow<List<NarsFeature>> = featureStore.allFeatures
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
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun setCurrentPhase(phase: PhaseDefinition): PhaseDefinition? {
        val currentIndex = featureStore.currentPhase.value?.index ?: 0
        if (phase.index > currentIndex) {
            val result = phaseNavigator.canAdvance(phase.index)
            if (result is PhaseNavigationResult.Blocked) {
                val message = getApplication<Application>().getString(result.messageResId)
                updateUiState(errorMessage = message)
                NarsLogger.d("MapViewModel", "Phase validation failed: $message")
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
        if (nextPhase != null) {
            return setCurrentPhase(nextPhase)
        }
        val currentIndex = featureStore.currentPhase.value?.index ?: 0
        val result = phaseNavigator.canAdvance(currentIndex + 1)
        if (result is PhaseNavigationResult.Blocked) {
            updateUiState(errorMessage = getApplication<Application>().getString(result.messageResId))
        }
        return null
    }

    fun goToPreviousPhase(): PhaseDefinition? {
        val prevPhase = phaseNavigator.goBack() ?: return null
        return setCurrentPhase(prevPhase)
    }

    fun canGoNextPhase(): Boolean = phaseNavigator.canGoForward()

    fun setReferenceRoad(dbId: String?) = featureStore.setReferenceRoad(dbId)

    fun undo(): Boolean {
        val action = featureStore.executeUndo()
        _canUndo.value = featureStore.canUndo
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

    fun addFeature(feature: NarsFeature) {
        featureStore.addFeature(feature, recordUndo = true)
        _canUndo.value = featureStore.canUndo
    }

    /** Restore a feature without recording an undo action (used for rollback). */
    fun restoreFeature(feature: NarsFeature) {
        featureStore.addFeature(feature, recordUndo = false)
        _canUndo.value = featureStore.canUndo
    }

    /**
     * Updates an existing store entry without recording an undo action.
     * Used when a newly drawn feature is persisted to the backend — the Create
     * undo action recorded at draw time already covers the feature's removal.
     */
    fun updateFeatureInPlace(feature: NarsFeature) {
        featureStore.updateFeature(feature.id, feature)
        _canUndo.value = featureStore.canUndo
    }

    fun addFeatures(features: List<NarsFeature>) {
        featureStore.addFeatures(features)
        _canUndo.value = featureStore.canUndo
    }

    fun updateFeature(feature: NarsFeature) {
        val oldFeature = featureStore.getFeatureById(feature.id)
        featureStore.updateFeature(feature.id, feature)
        oldFeature?.let { previous ->
            if (previous != feature) {
                featureStore.addUndoAction(
                    UndoAction.Update(
                        oldFeature = previous,
                        newFeature = feature,
                        phaseKey = feature.properties.phase,
                    ),
                )
            }
        }
        _canUndo.value = featureStore.canUndo
    }

    fun deleteFeature(featureId: String) {
        val feature = featureStore.getFeatureById(featureId)
        feature?.let {
            featureStore.addUndoAction(
                UndoAction.Delete(
                    feature = it,
                    phaseKey = it.properties.phase,
                ),
            )
        }
        featureStore.removeFeature(featureId)
        _canUndo.value = featureStore.canUndo
    }

    /**
     * Removes the most recent undo action that references a feature. Used when a
     * failed delete is rolled back locally: the Delete action recorded at delete
     * time would otherwise try to restore a feature that is still present.
     */
    fun clearDeleteUndo(featureId: String) {
        featureStore.removeMostRecentActionForFeature(featureId)
        _canUndo.value = featureStore.canUndo
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

    /** Clears all local feature state (used when a session expires). */
    fun clearAll() {
        featureStore.clearAll()
        _canUndo.value = false
    }

    fun updateUiState(isLoading: Boolean? = null, errorMessage: String? = null, successMessage: String? = null) {
        _uiState.update { current ->
            current.copy(
                isLoading = isLoading ?: current.isLoading,
                errorMessage = errorMessage ?: current.errorMessage,
                successMessage = successMessage ?: current.successMessage,
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)
