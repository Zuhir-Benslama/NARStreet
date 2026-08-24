package com.nars.maplibre.ui.screens

import android.content.Context
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.R
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.store.UndoAction
import com.nars.maplibre.modes.NarsGeoman
import com.nars.maplibre.utils.NarsLogger
import com.nars.maplibre.utils.isPointNearFeature
import kotlinx.coroutines.CoroutineScope
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * View-bound controller for map/Geoman interaction only. Backend persistence
 * lives in [MapViewModel] (viewModelScope-backed so in-flight operations
 * survive configuration changes); this class never talks to the network.
 */
class MapScreenHandlers(
    private val viewModel: MapViewModel,
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
                        viewModel.deleteFeatureOnBackend(featureId)
                    },
                )
            narsGeoman = geoman

            viewModel.currentPhase.value?.let { geoman.setCurrentPhase(it) }

            viewModel.currentUser?.let { user ->
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

    /**
     * Re-arms the interaction mode on a freshly created map. The drawing/edit
     * flags live in the ViewModel and survive configuration changes, but the
     * Geoman instance is rebuilt on the new map, so the active mode must be
     * restarted once features are displayed. (A partially drawn shape cannot be
     * reconstructed — only the mode itself is resumed.)
     */
    fun replayInteractionMode() {
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
}
