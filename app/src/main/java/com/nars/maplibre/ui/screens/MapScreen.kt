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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.UiState
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.User
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onNavigateToSettings: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val viewModel: MapViewModel = koinViewModel()

    val currentPhase by viewModel.currentPhase.collectAsState()
    val allFeatures by viewModel.allFeatures.collectAsState()
    val selectedFeature by viewModel.selectedFeature.collectAsState()
    val baseLayer by viewModel.baseLayer.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val drawingEnabled by viewModel.drawingEnabled.collectAsState()
    val editModeEnabled by viewModel.editModeEnabled.collectAsState()

    val handlers = remember {
        MapScreenHandlers(viewModel, context.applicationContext, scope) { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handlers.narsGeoman?.destroy()
            handlers.narsGeoman = null
        }
    }

    val featureCounts = remember(allFeatures) {
        allFeatures.groupingBy { it.properties.phase }.eachCount()
    }

    val effectContext = remember(viewModel, handlers, onLogout, snackbarHostState) {
        MapScreenEffectContext(viewModel, handlers, onLogout, snackbarHostState)
    }

    MapScreenEffects(effectContext, currentPhase, allFeatures, uiState)

    MapScreenScaffold(
        featureState = MapScreenFeatureState(
            currentPhase = currentPhase,
            allFeatures = allFeatures,
            selectedFeature = selectedFeature,
            featureCounts = featureCounts,
        ),
        uiState = MapScreenControlState(
            baseLayer = baseLayer,
            uiState = uiState,
            drawingEnabled = drawingEnabled,
            editModeEnabled = editModeEnabled,
        ),
        callbacks = MapScreenCallbacks(
            onNavigateToSettings = onNavigateToSettings,
            onLogout = onLogout,
            viewModel = viewModel,
            handlers = handlers,
            user = viewModel.currentUser,
        ),
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun MapScreenEffects(
    ctx: MapScreenEffectContext,
    currentPhase: PhaseDefinition?,
    allFeatures: List<NarsFeature>,
    uiState: UiState,
) {
    LaunchedEffect(Unit) {
        ctx.viewModel.sessionExpired.collect {
            NarsLogger.w("MapScreen", "Session expired — returning to login")
            ctx.viewModel.clearAll()
            ctx.handlers.narsGeoman?.displayManager?.updateDisplayedFeatures(emptyList())
            ctx.onSessionExpired()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            ctx.snackbarHostState.showSnackbar(it)
            ctx.viewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            ctx.snackbarHostState.showSnackbar(it)
            ctx.viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(allFeatures) {
        ctx.handlers.narsGeoman?.displayManager?.updateDisplayedFeatures(allFeatures)
    }

    LaunchedEffect(currentPhase) {
        currentPhase?.let { phase ->
            NarsLogger.d("MapScreen", "Phase changed: ${phase.label}")
            ctx.handlers.narsGeoman?.setCurrentPhase(phase)
            ctx.handlers.narsGeoman?.displayManager?.updateDisplayedFeatures(allFeatures)
            ctx.viewModel.updateUiState(isLoading = false)
        }
    }
}

internal data class MapScreenEffectContext(
    val viewModel: MapViewModel,
    val handlers: MapScreenHandlers,
    val onSessionExpired: () -> Unit,
    val snackbarHostState: SnackbarHostState,
)

internal data class MapScreenFeatureState(
    val currentPhase: PhaseDefinition?,
    val allFeatures: List<NarsFeature>,
    val selectedFeature: NarsFeature?,
    val featureCounts: Map<String, Int>,
)

internal data class MapScreenControlState(
    val baseLayer: BaseLayerType,
    val uiState: UiState,
    val drawingEnabled: Boolean,
    val editModeEnabled: Boolean,
)

internal data class FeatureModalState(
    val show: Boolean,
    val editingFeature: NarsFeature?,
    val currentPhase: PhaseDefinition?,
)

internal class FeatureModalActions(
    val onEditFeature: (NarsFeature) -> Unit,
    val onDismissModal: () -> Unit,
    val onSaveFeature: (NarsFeature) -> Unit,
    val onSaveEdits: () -> Unit,
    val onCancelEdits: () -> Unit,
)

internal class MapScreenCallbacks(
    val onNavigateToSettings: () -> Unit,
    val onLogout: () -> Unit,
    val viewModel: MapViewModel,
    val handlers: MapScreenHandlers,
    val user: User?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreenScaffold(
    featureState: MapScreenFeatureState,
    uiState: MapScreenControlState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
) {
    var showFeatureModal by rememberSaveable { mutableStateOf(false) }
    var editingFeatureId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingFeature = editingFeatureId?.let { id -> featureState.allFeatures.firstOrNull { it.id == id } }

    val modalState = FeatureModalState(
        show = showFeatureModal,
        editingFeature = editingFeature,
        currentPhase = featureState.currentPhase,
    )

    val modalActions = remember(editingFeature, callbacks) {
        FeatureModalActions(
            onEditFeature = { feature ->
                editingFeatureId = feature.id
                showFeatureModal = true
            },
            onDismissModal = {
                showFeatureModal = false
                editingFeatureId = null
            },
            onSaveFeature = { feature ->
                handleSaveFeature(feature, editingFeature, callbacks)
                showFeatureModal = false
                editingFeatureId = null
            },
            onSaveEdits = {
                callbacks.handlers.narsGeoman?.commitEdits()
                callbacks.viewModel.clearSelection()
                editingFeatureId = null
            },
            onCancelEdits = {
                callbacks.handlers.narsGeoman?.cancelEdits()
                callbacks.viewModel.clearSelection()
                editingFeatureId = null
            },
        )
    }

    MapScreenBody(
        featureState = featureState,
        uiState = uiState,
        callbacks = callbacks,
        snackbarHostState = snackbarHostState,
        modalState = modalState,
        modalActions = modalActions,
    )
}

private fun handleSaveFeature(feature: NarsFeature, existingFeature: NarsFeature?, callbacks: MapScreenCallbacks) {
    if (existingFeature != null && existingFeature.dbId != null) {
        val committed = callbacks.handlers.narsGeoman?.commitEdits(notify = false)
        val finalFeature = committed?.copy(properties = feature.properties) ?: feature
        callbacks.viewModel.updateFeature(finalFeature)
        callbacks.handlers.narsGeoman?.displayManager?.updateFeatureOnMap(finalFeature)
        callbacks.viewModel.updateFeatureOnBackend(finalFeature)
    } else if (existingFeature != null) {
        callbacks.viewModel.saveFeatureToBackend(feature)
    }
}
