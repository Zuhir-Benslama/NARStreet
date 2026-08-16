package com.nars.maplibre.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.R
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.User
import com.nars.maplibre.ui.components.CompactInfoPanel
import com.nars.maplibre.ui.components.NarsMap
import com.nars.maplibre.ui.components.ProfileMenu
import com.nars.maplibre.ui.components.TileControl
import com.nars.maplibre.ui.components.VerticalPhaseNav
import com.nars.maplibre.ui.theme.GlassBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreenBody(
    state: MapScreenViewState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
    showFeatureModal: Boolean,
    editingFeature: NarsFeature?,
    onEditFeature: (NarsFeature) -> Unit,
    onDismissModal: () -> Unit,
    onSaveFeature: (NarsFeature) -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
    ) { paddingValues ->
        MapScreenBoxContent(
            paddingValues = paddingValues,
            state = state,
            callbacks = callbacks,
            snackbarHostState = snackbarHostState,
            showFeatureModal = showFeatureModal,
            editingFeature = editingFeature,
            onEditFeature = onEditFeature,
            onDismissModal = onDismissModal,
            onSaveFeature = onSaveFeature,
            onSaveEdits = onSaveEdits,
            onCancelEdits = onCancelEdits,
        )
    }
}

@Composable
private fun MapScreenBoxContent(
    paddingValues: PaddingValues,
    state: MapScreenViewState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
    showFeatureModal: Boolean,
    editingFeature: NarsFeature?,
    onEditFeature: (NarsFeature) -> Unit,
    onDismissModal: () -> Unit,
    onSaveFeature: (NarsFeature) -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Use the color scheme's background so light mode isn't a solid dark
            // screen — the glass panels on top keep their translucent look.
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
    ) {
        MapScreenMapOverlay(
            viewModel = callbacks.viewModel,
            handlers = callbacks.handlers,
            drawingEnabled = state.drawingEnabled,
            editModeEnabled = state.editModeEnabled,
            onEditFeature = onEditFeature,
        )
        MapScreenProfileOverlay(
            modifier = Modifier.align(Alignment.TopEnd),
            user = callbacks.sessionManager.getUser(),
            onSettingsClick = callbacks.onNavigateToSettings,
            onLogoutClick = { callbacks.handlers.logout(callbacks.onLogout) },
        )
        MapScreenSidePanelWrapper(
            modifier = Modifier.align(Alignment.CenterEnd),
            currentPhase = state.currentPhase,
            featureCounts = state.featureCounts,
            baseLayer = state.baseLayer,
            viewModel = callbacks.viewModel,
            onUndo = { callbacks.handlers.undo() },
            snackbarHostState = snackbarHostState,
        )
        MapScreenCompactInfo(
            modifier = Modifier.align(Alignment.BottomStart),
            featureCounts = state.featureCounts,
            totalFeatures = state.allFeatures.size,
        )
        MapLoadingOverlay(isLoading = state.uiState.isLoading)
        MapScreenBottomSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedFeature = state.selectedFeature,
            editModeEnabled = state.editModeEnabled,
            editingFeature = editingFeature,
            onDismissFeature = { callbacks.viewModel.clearSelection() },
            onEditGeometry = { callbacks.handlers.toggleEditing(state.editModeEnabled) },
            onEditFeature = onEditFeature,
            onSaveEdits = onSaveEdits,
            onCancelEdits = onCancelEdits,
        )
        FeatureModalOverlay(
            editingFeature = editingFeature,
            currentPhase = state.currentPhase,
            showFeatureModal = showFeatureModal,
            onSave = onSaveFeature,
            onDismiss = onDismissModal,
        )
    }
}

@Composable
private fun MapScreenMapOverlay(
    viewModel: MapViewModel,
    handlers: MapScreenHandlers,
    drawingEnabled: Boolean,
    editModeEnabled: Boolean,
    onEditFeature: (NarsFeature) -> Unit,
) {
    NarsMap(
        viewModel = viewModel,
        onMapReady = { mv, map ->
            handlers.initializeNarsGeoman(mv, map)
            handlers.loadFeaturesOnMapReady()
        },
        onStyleLoaded = {
            handlers.narsGeoman?.displayManager?.onStyleReloaded(viewModel.allFeatures.value)
        },
        onMapClick = { latLng -> handlers.handleMapClick(latLng, drawingEnabled, editModeEnabled) },
        onMapLongClick = { latLng ->
            val clicked = handlers.handleMapLongClick(latLng)
            if (clicked != null) {
                onEditFeature(clicked)
            }
        },
        shouldHandleClick = { !drawingEnabled && !editModeEnabled },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MapScreenCompactInfo(modifier: Modifier = Modifier, featureCounts: Map<String, Int>, totalFeatures: Int) {
    CompactInfoPanel(
        featureCounts = featureCounts,
        totalFeatures = totalFeatures,
        modifier = modifier.padding(start = 12.dp, bottom = 12.dp).width(140.dp),
    )
}

@Composable
private fun MapScreenProfileOverlay(
    modifier: Modifier = Modifier,
    user: User?,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    ProfileMenu(
        user = user,
        onSettingsClick = onSettingsClick,
        onLogoutClick = onLogoutClick,
        modifier = modifier.padding(end = 12.dp, top = 12.dp),
    )
}

@Composable
private fun MapScreenSidePanelWrapper(
    modifier: Modifier = Modifier,
    currentPhase: PhaseDefinition?,
    featureCounts: Map<String, Int>,
    baseLayer: BaseLayerType,
    viewModel: MapViewModel,
    onUndo: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Box(modifier = modifier.padding(end = 12.dp)) {
        MapScreenSidePanel(
            currentPhase = currentPhase,
            featureCounts = featureCounts,
            baseLayer = baseLayer,
            viewModel = viewModel,
            onUndo = onUndo,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun MapScreenSidePanel(
    currentPhase: PhaseDefinition?,
    featureCounts: Map<String, Int>,
    baseLayer: BaseLayerType,
    viewModel: MapViewModel,
    onUndo: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canUndo by viewModel.canUndo.collectAsState()
    val phaseChangedText = stringResource(R.string.map_phase_changed)
    Column(
        modifier = Modifier
            .width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VerticalPhaseNav(
            currentPhaseIndex = currentPhase?.let { Phases.getIndexByKey(it.key) } ?: 0,
            phaseCounts = featureCounts,
            onPhaseSelected = { phase ->
                // Blocked transitions surface their (specific) message via
                // viewModel.errorMessage — do not double-show a generic one.
                viewModel.setCurrentPhase(phase)?.let {
                    val phaseLabel = Phases.getDisplayLabel(phase, context)
                    scope.launch { snackbarHostState.showSnackbar("$phaseChangedText: $phaseLabel") }
                }
            },
            modifier = Modifier.width(40.dp),
        )
        TileControl(currentLayer = baseLayer, onLayerSelected = { viewModel.setBaseLayer(it) })
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.width(40.dp).background(GlassBackground),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.map_undo),
            )
        }
    }
}

@Composable
private fun MapLoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
    }
}
