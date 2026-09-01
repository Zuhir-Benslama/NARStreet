package com.nars.maplibre

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.BackendInteractor
import com.nars.maplibre.data.api.SessionManager
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
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class MapViewModel(
    application: Application,
    private val featureStore: FeatureStoreInterface,
    private val appPreferences: AppPreferences,
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val interactor: BackendInteractor,
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MapViewModel"
    }

    private val phaseNavigator = PhaseNavigator(featureStore)

    val currentPhase: StateFlow<PhaseDefinition?> = featureStore.currentPhase
    val allFeatures: StateFlow<List<NarsFeature>> = featureStore.allFeatures
    val selectedFeature: StateFlow<NarsFeature?> = featureStore.selectedFeature

    /** Re-emitted when the backend rejects a refresh token (session dead). */
    val sessionExpired = apiService.sessionExpired

    val currentUser get() = appPreferences.user

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _baseLayer = MutableStateFlow(appPreferences.baseLayer)
    val baseLayer: StateFlow<BaseLayerType> = _baseLayer.asStateFlow()

    private val _drawingEnabled = MutableStateFlow(false)
    val drawingEnabled: StateFlow<Boolean> = _drawingEnabled.asStateFlow()

    private val _editModeEnabled = MutableStateFlow(false)
    val editModeEnabled: StateFlow<Boolean> = _editModeEnabled.asStateFlow()

    val referenceRoadDbId: StateFlow<String?> = featureStore.referenceRoadDbId

    /** Derived from the store's undo stack — no manual mirroring needed. */
    val canUndo: StateFlow<Boolean> = featureStore.undoState

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
        featureStore.selectFeature(null)
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

    /** Executes undo and returns the action that was undone (null if nothing). */
    fun undo(): UndoAction? {
        val action = featureStore.executeUndo()
        val app = getApplication<Application>()
        if (action == null) {
            updateUiState(errorMessage = app.getString(R.string.map_nothing_undo))
            return null
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
        return action
    }

    fun addFeature(feature: NarsFeature) {
        featureStore.addFeature(feature, recordUndo = true)
    }

    /** Restore a feature without recording an undo action (used for rollback). */
    fun restoreFeature(feature: NarsFeature) {
        featureStore.addFeature(feature, recordUndo = false)
    }

    /**
     * Attaches a backend id to the currently stored feature (matched by client
     * id) without overwriting any geometry/property edits made while the save
     * was in flight. Used when a newly drawn feature is persisted to the backend
     * — the Create undo action recorded at draw time already covers the
     * feature's removal.
     */
    fun updateFeatureInPlace(dbId: String, featureId: String) {
        val current = featureStore.getFeatureById(featureId) ?: return
        featureStore.updateFeature(featureId, current.copy(dbId = dbId))
    }

    fun addFeatures(features: List<NarsFeature>) {
        featureStore.addFeatures(features)
    }

    fun updateFeature(feature: NarsFeature) {
        featureStore.updateFeatureWithUndo(feature.id, feature)
    }

    fun deleteFeature(featureId: String) {
        val feature = featureStore.getFeatureById(featureId)
        feature?.let {
            featureStore.addUndoAction(
                UndoAction.Delete(feature = it),
            )
        }
        featureStore.removeFeature(featureId)
    }

    /**
     * Removes the most recent undo action that references a feature. Used when a
     * failed delete is rolled back locally: the Delete action recorded at delete
     * time would otherwise try to restore a feature that is still present.
     */
    fun clearDeleteUndo(featureId: String) {
        featureStore.removeMostRecentActionForFeature(featureId)
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

    /**
     * Loads all features from the backend into the store. Runs in
     * [viewModelScope] so an in-flight load survives configuration changes —
     * the previous composition-scoped version silently cancelled the load on
     * rotation. The map re-syncs through [allFeatures] emissions.
     */
    fun loadFeatures() {
        viewModelScope.launch {
            NarsLogger.d(TAG, "Loading features from backend...")
            updateUiState(isLoading = true)
            val result = interactor.loadFeatures()
            result.onSuccess { features ->
                addFeatures(features)
                val app = getApplication<Application>()
                val msg =
                    if (features.isEmpty()) {
                        app.getString(R.string.map_no_features)
                    } else {
                        app.resources.getQuantityString(
                            R.plurals.map_features_loaded,
                            features.size,
                            features.size,
                        )
                    }
                updateUiState(successMessage = msg)
            }
            result.onFailure {
                NarsLogger.e(TAG, "loadFeatures failed", it)
                updateUiState(errorMessage = "${appString(R.string.map_load_failed)}: ${it.message}")
            }
            updateUiState(isLoading = false)
        }
    }

    /**
     * Persists a newly created feature to the backend and attaches the
     * server-assigned id to the matching store entry.
     */
    fun saveFeatureToBackend(feature: NarsFeature) {
        viewModelScope.launch {
            val result = interactor.saveFeature(feature)
            result.onSuccess { savedId ->
                // Attach the backend id to the current store entry so any edits
                // made while the save was in flight are not overwritten.
                updateFeatureInPlace(savedId, feature.id)
                updateUiState(successMessage = appString(R.string.map_feature_saved))
            }
            result.onFailure {
                updateUiState(errorMessage = "${appString(R.string.map_save_failed)}: ${it.message}")
            }
        }
    }

    /** Pushes feature edits to the backend. */
    fun updateFeatureOnBackend(feature: NarsFeature) {
        viewModelScope.launch {
            val apiId = feature.dbId ?: feature.id
            val result = interactor.updateFeature(apiId, feature)
            result.onSuccess { updateUiState(successMessage = appString(R.string.map_feature_updated)) }
            result.onFailure {
                updateUiState(errorMessage = "${appString(R.string.map_update_failed)}: ${it.message}")
            }
        }
    }

    /**
     * Deletes a feature optimistically (store first, backend second), rolling
     * back the local delete when the backend rejects it. The map re-syncs via
     * [allFeatures]: the removal takes the feature off the map immediately and
     * a rollback re-adds it, so no view-layer calls are needed here.
     */
    fun deleteFeatureOnBackend(featureId: String) {
        val feature = featureStore.getFeatureById(featureId)
        deleteFeature(featureId)
        if (feature?.dbId == null) {
            // Local-only (unsaved) feature — nothing to delete on the backend.
            // Skipping the API call avoids a doomed DELETE (client UUID) that
            // fails and restores the feature the user just deleted.
            updateUiState(successMessage = appString(R.string.map_feature_deleted))
            return
        }
        viewModelScope.launch {
            val result = interactor.deleteFeature(feature.dbId)
            result.onSuccess { updateUiState(successMessage = appString(R.string.map_feature_deleted)) }
            result.onFailure {
                // Only roll back if the feature was not re-added meanwhile.
                if (featureStore.allFeatures.value.none { it.id == feature.id }) {
                    restoreFeature(feature)
                    clearDeleteUndo(feature.id)
                }
                updateUiState(errorMessage = "${appString(R.string.map_delete_failed)}: ${it.message}")
            }
        }
    }

    /**
     * Logs the user out. Navigation runs first (synchronously) so it can never
     * be skipped by a cancelled [viewModelScope]; server-side session
     * revocation then runs in the VM's scope. [SessionManager] clears the local
     * session regardless of the server outcome and runs non-cancellable, so a
     * scope cancellation cannot leave a half-logged-out device.
     */
    fun logout(onLogout: () -> Unit) {
        onLogout()
        viewModelScope.launch {
            sessionManager.logout()
        }
    }

    private fun appString(resId: Int): String = getApplication<Application>().getString(resId)

    /** Clears all local feature state (used when a session expires). */
    fun clearAll() {
        featureStore.clearAll()
    }

    fun updateUiState(isLoading: Boolean? = null, errorMessage: String? = null, successMessage: String? = null) {
        _uiState.update { current ->
            current.copy(
                isLoading = isLoading ?: current.isLoading,
                // A new message replaces the other kind so stale toasts do not linger.
                errorMessage = errorMessage ?: if (successMessage != null) null else current.errorMessage,
                successMessage = successMessage ?: if (errorMessage != null) null else current.successMessage,
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
