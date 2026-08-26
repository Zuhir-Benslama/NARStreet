package com.nars.maplibre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.nars.maplibre.R
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.ui.theme.Dimensions
import com.nars.maplibre.ui.theme.GlassBackground
import com.nars.maplibre.ui.theme.TextPrimary
import com.nars.maplibre.ui.theme.TextSecondary
import com.nars.maplibre.utils.formatDecimal
import com.nars.maplibre.utils.validateFeatureProperties

@Composable
fun FeatureValidationModal(
    feature: NarsFeature,
    phase: PhaseDefinition,
    onSave: (NarsFeature) -> Unit,
    onDismiss: () -> Unit,
) {
    // Re-lookup feature by ID at save time to avoid stale base if feature was
    // updated externally (e.g. server push) while the dialog was open. Keyed by
    // the feature so opening the dialog for a different feature resets the form.
    var props by remember(feature) { mutableStateOf(feature.properties) }
    var validationErrors by remember(feature) { mutableStateOf<Map<String, Int>>(emptyMap()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Dimensions.CardPadding),
            shape = RoundedCornerShape(Dimensions.CornerShapeLarge),
            colors = CardDefaults.cardColors(containerColor = GlassBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.ContentPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                FeatureModalHeader(phase = phase, onDismiss = onDismiss)
                Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
                FeatureModalCoordinateInfo(feature = feature)
                Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

                when (val typedProps = props) {
                    is FeatureProperties.RoadProperties -> RoadsValidationFields(
                        props = typedProps,
                        onPropsChanged = { props = it },
                    )

                    is FeatureProperties.HouseEntranceProperties -> HouseEntranceValidationFields(
                        props = typedProps,
                        onPropsChanged = { props = it },
                    )

                    is FeatureProperties.NamingPanelProperties -> NamingPanelValidationFields(
                        props = typedProps,
                        onPropsChanged = { props = it },
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))
                FeatureModalValidationErrors(errors = validationErrors)
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                FeatureModalSaveButton(
                    onSave = {
                        val result = validateFeatureProperties(props)
                        if (result.valid) {
                            onSave(feature.copy(properties = props))
                        } else {
                            validationErrors = result.errors
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FeatureModalHeader(phase: PhaseDefinition, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (phase.key) {
                Phases.ROADS_KEY -> stringResource(R.string.feature_road_attributes)
                Phases.HOUSE_ENTRANCES_KEY -> stringResource(R.string.feature_entrance_check)
                Phases.NAMING_PANELS_KEY -> stringResource(R.string.feature_panel_check)
                else -> stringResource(R.string.feature_details)
            },
            fontSize = Dimensions.TitleFontSize,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(Dimensions.IconButtonSize),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.map_close),
                tint = TextPrimary,
            )
        }
    }
}

@Composable
private fun FeatureModalCoordinateInfo(feature: NarsFeature) {
    val roadName = feature.properties.name ?: stringResource(R.string.feature_unnamed_road)
    Text(
        text = roadName,
        fontSize = Dimensions.NameFontSize,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
    )

    val coords = when (val geom = feature.geometry) {
        is LineStringGeometry -> {
            val c = geom.coordinates.chunked(2).filter { it.size == 2 }
            if (c.isNotEmpty()) {
                stringResource(
                    R.string.feature_coordinate_lat_lng,
                    c[0][1].formatDecimal(Dimensions.COORDINATE_PRECISION),
                    c[0][0].formatDecimal(Dimensions.COORDINATE_PRECISION),
                )
            } else {
                null
            }
        }

        is PointGeometry -> {
            if (geom.coordinates.size >= 2) {
                stringResource(
                    R.string.feature_coordinate_lat_lng,
                    geom.coordinates[1].formatDecimal(Dimensions.COORDINATE_PRECISION),
                    geom.coordinates[0].formatDecimal(Dimensions.COORDINATE_PRECISION),
                )
            } else {
                null
            }
        }

        else -> {
            null
        }
    }
    coords?.let {
        Text(text = it, fontSize = Dimensions.CaptionFontSize, color = TextSecondary)
    }
}

@Composable
private fun FeatureModalValidationErrors(errors: Map<String, Int>) {
    errors.entries.forEach { (field, msgResId) ->
        Text(
            text = "$field: ${stringResource(msgResId)}",
            fontSize = Dimensions.CaptionFontSize,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeatureModalSaveButton(onSave: () -> Unit) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(stringResource(R.string.feature_save))
    }
}
