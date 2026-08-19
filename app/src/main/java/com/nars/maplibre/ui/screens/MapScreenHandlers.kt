package com.nars.maplibre.ui.screens

import android.content.Context
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.R
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.store.UndoAction
import com.nars.maplibre.modes.NarsGeoman
import com.nars.maplibre.utils.NarsLogger
import com.nars.maplibre.utils.isPointNearFeature
import com.nars.maplibre.utils.retryOnTransientFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapScreenHandlers(
    private val viewModel: MapViewModel,
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: (String) -> Unit,
) {
    companion object {
        private const val TAG = "MapScreenHandlers"
        private const val ANIM_DURATION_MS = 1500
        private const val MAP_ZOOM = 14.0
        private const val LONG_CLICK_DISTANCE_THRESHOLD = 50.0
    }

    @Volatile var narsGeoman: NarsGeoman? = null

    val initializeNarsGeoman: (MapView, MapLibreMap) -> Unit =
        { mv, map ->
            val geoman =
                NarsGeoman(
                    mapView = mv,
                    map = map,
                    context = context,
                    scope = scope,
                    onFeatureCreated = { feature -> handleFeatureCreated(feature) },
                    onFeatureUpdated = { feature ->
                        viewModel.updateFeature(feature)
                        narsGeoman?.displayManager?.updateFeatureOnMap(feature)
                        snackbar(context.getString(R.string.map_feature_updated))
                    },
                    onFeatureDeleted = { featureId ->
                        deleteFeature(featureId)
                    },
                )
            narsGeoman = geoman

            viewModel.currentPhase.value?.let { geoman.setCurrentPhase(it) }

            sessionManager.getUser()?.let { user ->
                if (user.hasCommuneLocation()) {
                    val lat = user.communeLatitude ?: return@let
                    val lng = user.communeLongitude ?: return@let
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lat, lng),
                            MAP_ZOOM,
                        ),
                        ANIM_DURATION_MS,
                    )
                }
            }
        }

    fun handleFeatureCreated(feature: NarsFeature) {
        NarsLogger.d(TAG, "Feature created: ${feature.id}, phase=${feature.properties.phase}")
        viewModel.addFeature(feature)
        narsGeoman?.displayManager?.addFeature(feature)
    }

    private fun currentPhaseFeatures(): List<NarsFeature> {
        val currentPhaseKey = viewModel.currentPhase.value?.key
        return if (currentPhaseKey != null) {
            viewModel.allFeatures.value.filter { it.properties.phase == currentPhaseKey }
        } else {
            viewModel.allFeatures.value
        }
    }

    fun handleMapClick(latLng: LatLng, drawingEnabled: Boolean, editModeEnabled: Boolean) {
        if (drawingEnabled || editModeEnabled) {
            val snapped =
                if (drawingEnabled) {
                    narsGeoman?.snappingEngine?.snapPoint(latLng, viewModel.allFeatures.value) ?: latLng
                } else {
                    latLng
                }
            narsGeoman?.onMapClick(snapped)
            return
        }

        val clickedFeature =
            currentPhaseFeatures()
                .firstOrNull { feature -> isPointNearFeature(latLng, feature) }

        if (clickedFeature != null) {
            viewModel.selectFeature(clickedFeature)
        } else {
            viewModel.clearSelection()
        }
    }

    fun handleMapLongClick(latLng: LatLng): NarsFeature? {
        val clickedFeature =
            currentPhaseFeatures()
                .firstOrNull { feature ->
                    isPointNearFeature(latLng, feature, LONG_CLICK_DISTANCE_THRESHOLD)
                }

        if (clickedFeature != null) viewModel.selectFeature(clickedFeature)
        return clickedFeature
    }

    fun toggleDrawing(currentDrawingEnabled: Boolean) {
        if (currentDrawingEnabled) {
            viewModel.toggleDrawing(false)
            narsGeoman?.stopDrawing()
        } else {
            viewModel.toggleDrawing(true)
            viewModel.toggleEditMode(false)
            narsGeoman?.startDrawing()
        }
    }

    /** Undoes the last action and refreshes the affected feature on the map. */
    fun undo() {
        val action = viewModel.undo() ?: return
        val displayManager = narsGeoman?.displayManager
        when (action) {
            is UndoAction.Create -> displayManager?.removeFeature(action.feature.id)
            is UndoAction.Delete -> displayManager?.updateFeatureOnMap(action.feature)
            is UndoAction.Update -> displayManager?.updateFeatureOnMap(action.oldFeature)
        }
    }

    fun toggleEditing(currentEditEnabled: Boolean) {
        if (currentEditEnabled) {
            viewModel.toggleEditMode(false)
            narsGeoman?.stopEditing()
            viewModel.clearSelection()
        } else {
            viewModel.selectedFeature.value?.let { feature ->
                viewModel.toggleEditMode(true)
                viewModel.toggleDrawing(false)
                narsGeoman?.startEditing(feature)
                snackbar(context.getString(R.string.map_edit_hint))
            } ?: snackbar(context.getString(R.string.map_select_feature_hint))
        }
    }

    fun saveFeature(feature: NarsFeature) {
        scope.launch {
            val result = retryOnTransientFailure { apiService.saveFeature(feature) }
            result.onSuccess { savedId ->
                // Attach the backend id to the current store entry so any edits
                // made while the save was in flight are not overwritten.
                viewModel.updateFeatureInPlace(savedId, feature.id)
                val current = viewModel.allFeatures.value.find { it.id == feature.id }
                current?.let { narsGeoman?.displayManager?.updateFeatureOnMap(it) }
                snackbar(context.getString(R.string.map_feature_saved))
            }
            result.onFailure { snackbar("${context.getString(R.string.map_save_failed)}: ${it.message}") }
        }
    }

    fun updateFeature(feature: NarsFeature) {
        scope.launch {
            val apiId = feature.dbId ?: feature.id
            val result = retryOnTransientFailure { apiService.updateFeature(apiId, feature) }
            result.onSuccess { snackbar(context.getString(R.string.map_feature_updated)) }
            result.onFailure { snackbar("${context.getString(R.string.map_update_failed)}: ${it.message}") }
        }
    }

    fun deleteFeature(featureId: String) {
        val feature = viewModel.allFeatures.value.find { it.id == featureId }
        viewModel.deleteFeature(featureId)
        narsGeoman?.displayManager?.removeFeature(featureId)
        if (feature?.dbId == null) {
            // Local-only (unsaved) feature — nothing to delete on the backend.
            // Skipping the API call avoids a doomed DELETE (client UUID) that
            // fails and restores the feature the user just deleted.
            snackbar(context.getString(R.string.map_feature_deleted))
            return
        }
        scope.launch {
            val result = retryOnTransientFailure { apiService.deleteFeature(feature.dbId) }
            result.onSuccess { snackbar(context.getString(R.string.map_feature_deleted)) }
            result.onFailure {
                // Only roll back if the feature was not re-added meanwhile.
                if (viewModel.allFeatures.value.none { it.id == feature.id }) {
                    viewModel.restoreFeature(feature)
                    viewModel.clearDeleteUndo(feature.id)
                    narsGeoman?.displayManager?.addFeature(feature)
                }
                snackbar("${context.getString(R.string.map_delete_failed)}: ${it.message}")
            }
        }
    }

    /**
     * Re-arms the interaction mode on a freshly created map. The drawing/edit
     * flags live in the ViewModel and survive configuration changes, but the
     * Geoman instance is rebuilt on the new map, so the active mode must be
     * restarted once features are displayed. (A partially drawn shape cannot be
     * reconstructed — only the mode itself is resumed.)
     */
    private fun replayInteractionMode() {
        val geoman = narsGeoman ?: return
        when {
            viewModel.drawingEnabled.value -> {
                geoman.startDrawing()
            }
            viewModel.editModeEnabled.value -> {
                viewModel.selectedFeature.value?.let { feature ->
                    geoman.startEditing(feature)
                }
            }
        }
    }

    private suspend fun loadFeaturesWithRetry(): Result<List<NarsFeature>> =
        retryOnTransientFailure { apiService.loadFeatures() }

    fun logout(onLogout: () -> Unit) {
        scope.launch {
            sessionManager.logout()
            onLogout()
        }
    }

    fun loadFeaturesOnMapReady() {
        scope.launch {
            NarsLogger.d(TAG, "Loading features from backend...")
            viewModel.updateUiState(isLoading = true)
            val result = loadFeaturesWithRetry()
            result.onSuccess { features ->
                viewModel.addFeatures(features)
                narsGeoman?.displayManager?.updateDisplayedFeatures(features)
                replayInteractionMode()
                val msg =
                    if (features.isEmpty()) {
                        context.getString(R.string.map_no_features)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.map_features_loaded,
                            features.size,
                            features.size,
                        )
                    }
                snackbar(msg)
            }
            result.onFailure { snackbar("${context.getString(R.string.map_load_failed)}: ${it.message}") }
            viewModel.updateUiState(isLoading = false)
        }
    }
}
