package com.nars.maplibre.utils

import androidx.annotation.StringRes
import com.nars.maplibre.R
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.store.FeatureStoreInterface

sealed class PhaseNavigationResult {
    data object Allowed : PhaseNavigationResult()

    data class Blocked(@StringRes val messageResId: Int) : PhaseNavigationResult()
}

/**
 * Phase navigation validation for NARStreet Field Mode
 * Only 3 phases: Roads, HouseEntrances, NamingPanels
 */
class PhaseNavigator(private val featureStore: FeatureStoreInterface) {
    /**
     * Check if user can advance to next phase
     */
    fun canAdvance(targetPhaseIndex: Int): PhaseNavigationResult {
        val currentPhase = featureStore.currentPhase.value
        if (currentPhase == null) return PhaseNavigationResult.Blocked(R.string.alert_no_phase)
        if (Phases.getByIndex(targetPhaseIndex) == null) {
            return PhaseNavigationResult.Blocked(R.string.alert_invalid_phase)
        }
        if (targetPhaseIndex <= currentPhase.index) return PhaseNavigationResult.Allowed

        // Advancing forward means leaving every phase between the current one
        // and the target (a forward jump must not skip a phase's requirement).
        val phasesToPass = (currentPhase.index until targetPhaseIndex).mapNotNull(Phases::getByIndex)
        var result: PhaseNavigationResult = PhaseNavigationResult.Allowed
        for (phase in phasesToPass) {
            val blockedRes = when (phase.key) {
                Phases.ROADS_KEY ->
                    if (featureStore.getFeaturesByPhase(Phases.ROADS_KEY).isEmpty()) {
                        R.string.alert_at_least_one_road
                    } else {
                        null
                    }

                Phases.HOUSE_ENTRANCES_KEY ->
                    if (featureStore.getFeaturesByPhase(Phases.HOUSE_ENTRANCES_KEY).isEmpty()) {
                        R.string.alert_at_least_one_entrance
                    } else {
                        null
                    }

                else -> null
            }
            if (blockedRes != null) {
                result = PhaseNavigationResult.Blocked(blockedRes)
                break
            }
        }
        return result
    }

    /**
     * Check if at least one road exists
     */
    fun hasAnyRoads(): Boolean = featureStore.getFeaturesByPhase(Phases.ROADS_KEY).isNotEmpty()

    /**
     * Try to navigate to target phase
     * @return PhaseDefinition if navigation allowed, null if blocked
     */
    fun navigateTo(targetIndex: Int): PhaseDefinition? {
        val result = canAdvance(targetIndex)
        if (result is PhaseNavigationResult.Blocked) {
            return null
        }
        return Phases.getByIndex(targetIndex)
    }

    /**
     * Get the previous phase index
     */
    fun getPreviousPhaseIndex(): Int? {
        val current = featureStore.currentPhase.value ?: return null
        return if (current.index > 0) current.index - 1 else null
    }

    /**
     * Get the next phase index
     */
    fun getNextPhaseIndex(): Int? {
        val current = featureStore.currentPhase.value ?: return null
        return if (current.index < Phases.ALL.size - 1) current.index + 1 else null
    }

    /**
     * Check if can go to previous phase
     */
    fun canGoBack(): Boolean = getPreviousPhaseIndex() != null

    /**
     * Check if can go to next phase
     */
    fun canGoForward(): Boolean {
        val nextIndex = getNextPhaseIndex() ?: return false
        return canAdvance(nextIndex) is PhaseNavigationResult.Allowed
    }

    /**
     * Go to previous phase
     */
    fun goBack(): PhaseDefinition? {
        val prevIndex = getPreviousPhaseIndex() ?: return null
        return Phases.getByIndex(prevIndex)
    }

    /**
     * Try to go to next phase - validates requirements first
     * @return PhaseDefinition if allowed, null if blocked
     */
    fun goNext(): PhaseDefinition? {
        val nextIndex = getNextPhaseIndex() ?: return null
        return navigateTo(nextIndex)
    }
}
