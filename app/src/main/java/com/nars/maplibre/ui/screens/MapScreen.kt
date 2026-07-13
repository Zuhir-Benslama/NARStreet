package com.nars.maplibre.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.R
import com.nars.maplibre.UiState
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.User
import com.nars.maplibre.ui.components.CompactInfoPanel
import com.nars.maplibre.ui.components.FeatureValidationModal
import com.nars.maplibre.ui.components.NarsMap
import com.nars.maplibre.ui.components.ProfileMenu
import com.nars.maplibre.ui.components.TileControl
import com.nars.maplibre.ui.components.VerticalPhaseNav
import com.nars.maplibre.ui.theme.GlassBackground
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onNavigateToSettings: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MapViewModel = koinViewModel()
    val apiService: ApiService = get(ApiService::class.java)
    val sessionManager: SessionManager = get(SessionManager::class.java)

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

    LaunchedEffect(handlers.narsGeoman) {
        handlers.narsGeoman?.let { handlers.loadFeaturesOnMapReady() }
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

@Stable
private data class MapScreenViewState(
    val currentPhase: PhaseDefinition?,
    val allFeatures: List<NarsFeature>,
    val selectedFeature: NarsFeature?,
    val baseLayer: BaseLayerType,
    val uiState: UiState,
    val drawingEnabled: Boolean,
    val editModeEnabled: Boolean,
    val featureCounts: Map<String, Int>,
)

@Stable
private class MapScreenCallbacks(
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
                callbacks.viewModel.updateFeature(feature)
                callbacks.handlers.narsGeoman?.commitEdits()
                callbacks.handlers.narsGeoman?.displayManager?.updateFeatureOnMap(feature)
                callbacks.handlers.updateFeature(feature)
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

@Composable
private fun SelectedFeatureCardActions(
    editModeEnabled: Boolean,
    isCurrentlyEditing: Boolean,
    onShowProperties: () -> Unit,
    onEditGeometry: () -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editModeEnabled && isCurrentlyEditing) {
            Button(
                onClick = onSaveEdits,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.map_save_geometry))
            }
            Button(
                onClick = onCancelEdits,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.map_cancel)) }
        } else {
            Button(
                onClick = onShowProperties,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.map_properties)) }
            Button(
                onClick = onEditGeometry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.map_edit_geometry))
            }
        }
    }
}

@Composable
private fun MapScreenFeatureSheet(
    selectedFeature: NarsFeature?,
    editModeEnabled: Boolean,
    editingFeature: NarsFeature?,
    onDismissFeature: () -> Unit,
    onEditGeometry: () -> Unit,
    onEditFeature: (NarsFeature) -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    selectedFeature?.let { feature ->
        SelectedFeatureCard(
            feature = feature,
            editModeEnabled = editModeEnabled,
            isCurrentlyEditing = editingFeature?.id == feature.id,
            onDismiss = onDismissFeature,
            onEditGeometry = onEditGeometry,
            onShowProperties = { onEditFeature(feature) },
            onSaveEdits = onSaveEdits,
            onCancelEdits = onCancelEdits,
        )
    }
}

@Composable
private fun MapScreenBody(
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
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
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
        modifier = Modifier.fillMaxSize().background(GlassBackground).padding(paddingValues),
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
    snackbarHostState: SnackbarHostState,
) {
    Box(modifier = modifier.padding(end = 12.dp)) {
        MapScreenSidePanel(
            currentPhase = currentPhase,
            featureCounts = featureCounts,
            baseLayer = baseLayer,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun MapScreenBottomSheet(
    modifier: Modifier = Modifier,
    selectedFeature: NarsFeature?,
    editModeEnabled: Boolean,
    editingFeature: NarsFeature?,
    onDismissFeature: () -> Unit,
    onEditGeometry: () -> Unit,
    onEditFeature: (NarsFeature) -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        MapScreenFeatureSheet(
            selectedFeature = selectedFeature,
            editModeEnabled = editModeEnabled,
            editingFeature = editingFeature,
            onDismissFeature = onDismissFeature,
            onEditGeometry = onEditGeometry,
            onEditFeature = onEditFeature,
            onSaveEdits = onSaveEdits,
            onCancelEdits = onCancelEdits,
        )
    }
}

@Composable
private fun FeatureModalOverlay(
    editingFeature: NarsFeature?,
    currentPhase: PhaseDefinition?,
    showFeatureModal: Boolean,
    onSave: (NarsFeature) -> Unit,
    onDismiss: () -> Unit,
) {
    val feature = editingFeature ?: return
    val phase = currentPhase ?: return
    if (showFeatureModal) {
        FeatureValidationModal(
            feature = feature,
            phase = phase,
            onSave = onSave,
            onDismiss = onDismiss,
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
        onMapReady = { mv, map -> handlers.initializeNarsGeoman(mv, map) },
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
private fun MapScreenSidePanel(
    currentPhase: PhaseDefinition?,
    featureCounts: Map<String, Int>,
    baseLayer: BaseLayerType,
    viewModel: MapViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val phaseChangedText = stringResource(R.string.map_phase_changed)
    val cannotAdvanceText = stringResource(R.string.map_cannot_advance)
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
                    scope.launch { snackbarHostState.showSnackbar("$phaseChangedText: ${phase.label}") }
                } ?: scope.launch { snackbarHostState.showSnackbar(cannotAdvanceText) }
            },
            modifier = Modifier.width(40.dp),
        )
        TileControl(currentLayer = baseLayer, onLayerSelected = { viewModel.setBaseLayer(it) })
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

@Composable
private fun SelectedFeatureCard(
    feature: NarsFeature,
    editModeEnabled: Boolean,
    isCurrentlyEditing: Boolean,
    onDismiss: () -> Unit,
    onEditGeometry: () -> Unit,
    onShowProperties: () -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdits: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = feature.properties.name ?: feature.type.value, fontSize = 14.sp)
                    Text(
                        text = feature.properties.phase,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.map_close))
                }
            }
            SelectedFeatureCardActions(
                editModeEnabled = editModeEnabled,
                isCurrentlyEditing = isCurrentlyEditing,
                onShowProperties = onShowProperties,
                onEditGeometry = onEditGeometry,
                onSaveEdits = onSaveEdits,
                onCancelEdits = onCancelEdits,
            )
        }
    }
}
