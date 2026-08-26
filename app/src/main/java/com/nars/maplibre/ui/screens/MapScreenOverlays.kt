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
    featureState: MapScreenFeatureState,
    uiState: MapScreenControlState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
    modalState: FeatureModalState,
    modalActions: FeatureModalActions,
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
            featureState = featureState,
            uiState = uiState,
            callbacks = callbacks,
            snackbarHostState = snackbarHostState,
            modalState = modalState,
            modalActions = modalActions,
        )
    }
}

@Composable
private fun MapScreenBoxContent(
    paddingValues: PaddingValues,
    featureState: MapScreenFeatureState,
    uiState: MapScreenControlState,
    callbacks: MapScreenCallbacks,
    snackbarHostState: SnackbarHostState,
    modalState: FeatureModalState,
    modalActions: FeatureModalActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
    ) {
        MapScreenMapOverlay(
            viewModel = callbacks.viewModel,
            handlers = callbacks.handlers,
            drawingEnabled = uiState.drawingEnabled,
            editModeEnabled = uiState.editModeEnabled,
            onEditFeature = modalActions.onEditFeature,
        )
        MapScreenProfileOverlay(
            modifier = Modifier.align(Alignment.TopEnd),
            user = callbacks.user,
            onSettingsClick = callbacks.onNavigateToSettings,
            onLogoutClick = { callbacks.viewModel.logout(callbacks.onLogout) },
        )
        MapScreenSidePanelWrapper(
            modifier = Modifier.align(Alignment.CenterEnd),
            currentPhase = featureState.currentPhase,
            featureCounts = featureState.featureCounts,
            baseLayer = uiState.baseLayer,
            viewModel = callbacks.viewModel,
            onUndo = { callbacks.handlers.undo() },
            snackbarHostState = snackbarHostState,
        )
        MapScreenCompactInfo(
            modifier = Modifier.align(Alignment.BottomStart),
            featureCounts = featureState.featureCounts,
            totalFeatures = featureState.allFeatures.size,
        )
        MapLoadingOverlay(isLoading = uiState.uiState.isLoading)
        MapScreenBottomSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedFeature = featureState.selectedFeature,
            editModeEnabled = uiState.editModeEnabled,
            editingFeature = modalState.editingFeature,
            onDismissFeature = { callbacks.viewModel.clearSelection() },
            onEditGeometry = { callbacks.handlers.toggleEditing(uiState.editModeEnabled) },
            onEditFeature = modalActions.onEditFeature,
            onSaveEdits = modalActions.onSaveEdits,
            onCancelEdits = modalActions.onCancelEdits,
        )
        FeatureModalOverlay(
            editingFeature = modalState.editingFeature,
            currentPhase = modalState.currentPhase,
            showFeatureModal = modalState.show,
            onSave = modalActions.onSaveFeature,
            onDismiss = modalActions.onDismissModal,
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
        callbacks = com.nars.maplibre.ui.components.NarsMapCallbacks(
            onMapReady = { mv, map ->
                handlers.initializeNarsGeoman(mv, map)
                viewModel.loadFeatures()
                handlers.replayInteractionMode()
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
        ),
        viewModel = viewModel,
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
