package com.nars.maplibre.ui.components

import androidx.compose.ui.graphics.Color
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases

data class PhaseState(
    val isDone: Boolean,
    val isActive: Boolean,
    val count: Int,
    val phaseColor: Color,
    val badge: String
)

fun computePhaseState(
    index: Int,
    phase: PhaseDefinition,
    currentPhase: PhaseDefinition?,
    phaseCounts: Map<String, Int>
): PhaseState {
    val isDone = currentPhase?.let { cp ->
        Phases.ALL.indexOfFirst { it.key == cp.key } > index
    } ?: false
    val isActive = currentPhase?.key == phase.key
    val count = phaseCounts[phase.key] ?: 0
    return PhaseState(
        isDone = isDone,
        isActive = isActive,
        count = count,
        phaseColor = phase.parsedColor,
        badge = if (isDone) "✓" else "${index + 1}"
    )
}
