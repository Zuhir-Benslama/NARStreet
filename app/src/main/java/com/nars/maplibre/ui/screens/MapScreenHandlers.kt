package com.nars.maplibre.ui.screens

import android.content.Context
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.R
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.modes.NarsGeoman
import com.nars.maplibre.utils.NarsLogger
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
        private const val MIN_CIRCLE_RADIUS = 10.0
        private const val ANIM_DURATION_MS = 1500
        private const val MAP_ZOOM = 14.0
        private const val LONG_CLICK_DISTANCE_THRESHOLD = 50.0
        private const val NEAR_FEATURE_DISTANCE_THRESHOLD = 20.0
    }

    var narsGeoman: NarsGeoman? = null

    val initializeNarsGeoman: (MapView, MapLibreMap) -> Unit =
        { mv, map ->
            val geoman =
                NarsGeoman(
                    mapView = mv,
                    map = map,
                    context = context,
                    onFeatureCreated = { feature -> handleFeatureCreated(feature) },
                    onFeatureUpdated = { feature ->
                        viewModel.updateFeature(feature)
                        snackbar(context.getString(R.string.map_feature_updated))
                    },
                    onFeatureDeleted = { featureId ->
                        viewModel.deleteFeature(featureId)
                        snackbar(context.getString(R.string.map_feature_deleted))
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
                    when (val geometry = feature.geometry) {
                        is PointGeometry -> {
                            val fp = LatLng(geometry.coordinates[1], geometry.coordinates[0])
                            latLng.distanceTo(fp) < LONG_CLICK_DISTANCE_THRESHOLD
                        }

                        is LineStringGeometry -> {
                            geometry.coordinates.chunked(2).filter { it.size == 2 }.any { coord ->
                                val lp = LatLng(coord[1], coord[0])
                                latLng.distanceTo(lp) < LONG_CLICK_DISTANCE_THRESHOLD
                            }
                        }

                        else -> {
                            false
                        }
                    }
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
            val result = apiService.saveFeature(feature)
            result.onSuccess { savedId ->
                val updatedFeature =
                    if (savedId != feature.id) {
                        feature.copy(dbId = savedId, id = savedId)
                    } else {
                        feature.copy(dbId = savedId)
                    }
                viewModel.addFeature(updatedFeature)
                narsGeoman?.displayManager?.updateFeatureId(feature.id, updatedFeature.id)
                narsGeoman?.displayManager?.updateFeatureOnMap(updatedFeature)
                snackbar(context.getString(R.string.map_feature_saved))
            }
            result.onFailure { snackbar("${context.getString(R.string.map_save_failed)}: ${it.message}") }
        }
    }

    fun updateFeature(feature: NarsFeature) {
        scope.launch {
            val result = apiService.updateFeature(feature.id, feature)
            result.onSuccess { snackbar(context.getString(R.string.map_feature_updated)) }
            result.onFailure { snackbar("${context.getString(R.string.map_update_failed)}: ${it.message}") }
        }
    }

    fun deleteFeature(featureId: String) {
        viewModel.deleteFeature(featureId)
        narsGeoman?.displayManager?.removeFeature(featureId)
        scope.launch {
            val result = apiService.deleteFeature(featureId)
            result.onSuccess { snackbar(context.getString(R.string.map_feature_deleted)) }
            result.onFailure { snackbar("${context.getString(R.string.map_delete_failed)}: ${it.message}") }
        }
    }

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
            val result = apiService.loadFeatures()
            result.onSuccess { features ->
                viewModel.featureStore.addFeatures(features)
                narsGeoman?.displayManager?.updateDisplayedFeatures(features)
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
            result.onFailure { snackbar("${context.getString(R.string.map_save_failed)}: ${it.message}") }
            viewModel.updateUiState(isLoading = false)
        }
    }

    private fun isPointNearFeature(latLng: LatLng, feature: NarsFeature): Boolean {
        val threshold = NEAR_FEATURE_DISTANCE_THRESHOLD
        return when (val geometry = feature.geometry) {
            is PointGeometry -> {
                val fp = LatLng(geometry.coordinates[1], geometry.coordinates[0])
                latLng.distanceTo(fp) < threshold
            }

            is CircleGeometry -> {
                val cp = LatLng(geometry.coordinates[1], geometry.coordinates[0])
                latLng.distanceTo(cp) < geometry.coordinates[2].coerceAtLeast(MIN_CIRCLE_RADIUS)
            }

            is LineStringGeometry -> {
                geometry.coordinates.chunked(2).filter { it.size == 2 }.any { coord ->
                    val lp = LatLng(coord[1], coord[0])
                    latLng.distanceTo(lp) < threshold
                }
            }

            is PolygonGeometry -> {
                geometry.coordinates.chunked(2).filter { it.size == 2 }.any { coord ->
                    val pp = LatLng(coord[1], coord[0])
                    latLng.distanceTo(pp) < threshold
                }
            }
        }
    }
}
