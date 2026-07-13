package com.nars.maplibre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nars.maplibre.R
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.ui.theme.GlassBackground
import com.nars.maplibre.ui.theme.PrimaryColor
import com.nars.maplibre.ui.theme.SuccessColor
import com.nars.maplibre.utils.NarsLogger

/**
 * Vertical Phase Navigation Sidebar
 * Matches web app design but positioned vertically on the right side
 */
@Composable
fun VerticalPhaseNav(
    currentPhaseIndex: Int,
    phaseCounts: Map<String, Int>,
    onPhaseSelected: (PhaseDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                GlassBackground.copy(alpha = 0.95f),
            )
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val allPhases = Phases.ALL
        val hasFeaturesPerPhase = allPhases.map { phase ->
            (phaseCounts[phase.key] ?: 0) > 0
        }
        val previousAllDone = mutableListOf(false)
        for (i in 1 until allPhases.size) {
            val prevDone = previousAllDone[i - 1] && hasFeaturesPerPhase[i - 1]
            previousAllDone.add(prevDone)
        }

        allPhases.forEachIndexed { index, phase ->
            val phaseHasFeatures = hasFeaturesPerPhase[index]
            val isDone = phaseHasFeatures
            val isActive = index == currentPhaseIndex
            val isLocked = index == 0 || !previousAllDone[index - 1]

            // Phase badge with larger touch target
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PhaseBadge(
                    phase = phase,
                    isDone = isDone,
                    isActive = isActive,
                    isLocked = isLocked,
                    count = phaseCounts[phase.key] ?: 0,
                    badgeIndex = index + 1,
                    onClick = { onPhaseSelected(phase) },
                )
            }

            // Connector (except for last phase)
            if (index < Phases.ALL.lastIndex) {
                PhaseConnector(isDone = isDone)
            }
        }
    }
}

private data class PhaseBadgeColors(val background: Color, val border: Color, val badge: Color)

private fun phaseBadgeColors(isDone: Boolean, isActive: Boolean): PhaseBadgeColors = if (isDone) {
    PhaseBadgeColors(
        background = Color.White.copy(alpha = 0.15f),
        border = Color.White.copy(alpha = 0.35f),
        badge = Color.White,
    )
} else if (isActive) {
    PhaseBadgeColors(
        background = Color.White.copy(alpha = 0.28f),
        border = Color.White.copy(alpha = 0.80f),
        badge = Color.White,
    )
} else {
    PhaseBadgeColors(
        background = Color.White.copy(alpha = 0.05f),
        border = Color.White.copy(alpha = 0.15f),
        badge = Color.White.copy(alpha = 0.30f),
    )
}

/**
 * Phase badge (circle with number or checkmark)
 */
@Composable
private fun PhaseBadge(
    phase: PhaseDefinition,
    isDone: Boolean,
    isActive: Boolean,
    isLocked: Boolean,
    count: Int,
    badgeIndex: Int,
    onClick: () -> Unit,
) {
    val colors = phaseBadgeColors(isDone, isActive)
    val badgeContent = if (isDone) "✓" else "$badgeIndex"

    val badgeLabel = stringResource(R.string.map_phase_badge_label, phase.label)

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.background)
            .semantics { contentDescription = badgeLabel }
            .pointerInput(isLocked, phase.key) {
                detectTapGestures(
                    onTap = {
                        NarsLogger.d("PhaseBadge", "Tapped phase: ${phase.label}, isLocked=$isLocked")
                        if (!isLocked) {
                            NarsLogger.d("PhaseBadge", "Calling onClick for ${phase.label}")
                            onClick()
                        } else {
                            NarsLogger.d("PhaseBadge", "Phase ${phase.label} is LOCKED - ignoring click")
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PhaseBadgeContent(colors, badgeContent, isActive)
    }

    PhaseCountDot(
        isDone = isDone,
        isActive = isActive,
        isLocked = isLocked,
        count = count,
    )
}

@Composable
private fun PhaseBadgeContent(colors: PhaseBadgeColors, badgeContent: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.border),
    )
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(colors.badge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeContent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = GlassBackground,
        )
    }
    if (isActive) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(PrimaryColor.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun PhaseCountDot(isDone: Boolean, isActive: Boolean, isLocked: Boolean, count: Int) {
    if (count > 0 && !isLocked) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isDone -> SuccessColor
                        isActive -> PrimaryColor
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                ),
        )
    }
}

/**
 * Vertical connector between phases
 */
@Composable
private fun PhaseConnector(isDone: Boolean) {
    Spacer(
        modifier = Modifier
            .width(2.dp)
            .height(12.dp)
            .background(
                when {
                    isDone -> Color.White.copy(alpha = 0.50f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
            ),
    )
}
