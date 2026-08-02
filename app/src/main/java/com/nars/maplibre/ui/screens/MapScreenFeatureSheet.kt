package com.nars.maplibre.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nars.maplibre.R
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.ui.components.FeatureValidationModal
import com.nars.maplibre.ui.theme.GlassBackground

@Composable
internal fun MapScreenBottomSheet(
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
internal fun FeatureModalOverlay(
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
