package com.nars.maplibre.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.UiState
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onNavigateToSettings: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val viewModel: MapViewModel = koinViewModel()
    val apiService: ApiService = koinInject()
    val sessionManager: SessionManager = koinInject()

    val currentPhase by viewModel.currentPhase.collectAsState()
    val allFeatures by viewModel.allFeatures.collectAsState()
    val selectedFeature by viewModel.selectedFeature.collectAsState()
    val baseLayer by viewModel.baseLayer.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val drawingEnabled by viewModel.drawingEnabled.collectAsState()
    val editModeEnabled by viewModel.editModeEnabled.collectAsState()

    val handlers = remember {
        MapScreenHandlers(viewModel, apiService, sessionManager, context.applicationContext, scope) { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handlers.narsGeoman?.destroy()
        }
    }

    val featureCounts = remember(allFeatures) {
        allFeatures.groupingBy { it.properties.phase }.eachCount()
    }

    MapScreenEffects(viewModel, handlers, currentPhase, allFeatures, uiState, snackbarHostState)

    MapScreenScaffold(
        state = MapScreenViewState(
            currentPhase = currentPhase,
            allFeatures = allFeatures,
            selectedFeature = selectedFeature,
            baseLayer = baseLayer,
            uiState = uiState,
            drawingEnabled = drawingEnabled,
            editModeEnabled = editModeEnabled,
            featureCounts = featureCounts,
        ),
        callbacks = MapScreenCallbacks(
            onNavigateToSettings = onNavigateToSettings,
            onLogout = onLogout,
            viewModel = viewModel,
            handlers = handlers,
            sessionManager = sessionManager,
        ),
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun MapScreenEffects(
    viewModel: MapViewModel,
    handlers: MapScreenHandlers,
    currentPhase: PhaseDefinition?,
    allFeatures: List<NarsFeature>,
    uiState: UiState,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(allFeatures) {
        handlers.narsGeoman?.displayManager?.updateDisplayedFeatures(allFeatures)
    }

    LaunchedEffect(currentPhase) {
        currentPhase?.let { phase ->
            NarsLogger.d("MapScreen", "Phase changed: ${phase.label}")
            handlers.narsGeoman?.setCurrentPhase(phase)
            handlers.narsGeoman?.displayManager?.updateDisplayedFeatures(allFeatures)
            viewModel.updateUiState(isLoading = false)
        }
    }
}

internal data class MapScreenViewState(
    val currentPhase: PhaseDefinition?,
    val allFeatures: List<NarsFeature>,
    val selectedFeature: NarsFeature?,
    val baseLayer: BaseLayerType,
    val uiState: UiState,
    val drawingEnabled: Boolean,
    val editModeEnabled: Boolean,
    val featureCounts: Map<String, Int>,
)

internal class MapScreenCallbacks(
    val onNavigateToSettings: () -> Unit,
    val onLogout: () -> Unit,
    val viewModel: MapViewModel,
    val handlers: MapScreenHandlers,
    val sessionManager: SessionManager,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreenScaffold(
    state: MapScreenViewState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
) {
    var showFeatureModal by remember { mutableStateOf(false) }
    var editingFeature by remember { mutableStateOf<NarsFeature?>(null) }

    MapScreenBody(
        state = state,
        callbacks = callbacks,
        snackbarHostState = snackbarHostState,
        showFeatureModal = showFeatureModal,
        editingFeature = editingFeature,
        onEditFeature = { feature ->
            editingFeature = feature
            showFeatureModal = true
        },
        onDismissModal = {
            showFeatureModal = false
            editingFeature = null
        },
        onSaveFeature = { feature ->
            val existing = editingFeature
            if (existing != null && existing.dbId != null) {
                val committed = callbacks.handlers.narsGeoman?.commitEdits(notify = false)
                val finalFeature =
                    if (committed != null) {
                        committed.copy(properties = feature.properties)
                    } else {
                        feature
                    }
                callbacks.viewModel.updateFeature(finalFeature)
                callbacks.handlers.narsGeoman?.displayManager?.updateFeatureOnMap(finalFeature)
                callbacks.handlers.updateFeature(finalFeature)
            } else if (existing != null) {
                callbacks.handlers.saveFeature(feature)
            }
            showFeatureModal = false
            editingFeature = null
        },
        onSaveEdits = {
            callbacks.handlers.narsGeoman?.commitEdits()
            callbacks.viewModel.clearSelection()
            editingFeature = null
        },
        onCancelEdits = {
            callbacks.handlers.narsGeoman?.cancelEdits()
            callbacks.viewModel.clearSelection()
            editingFeature = null
        },
    )
}
