package com.nars.maplibre.utils

import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.store.FeatureStoreInterface

sealed class PhaseNavigationResult {
    data object Allowed : PhaseNavigationResult()

    data class Blocked(val message: String) : PhaseNavigationResult()
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
        fun blocked(msg: String) = PhaseNavigationResult.Blocked(msg)
        val allowed = PhaseNavigationResult.Allowed
        val currentPhase = featureStore.currentPhase.value ?: return blocked("alert_no_phase")
        Phases.getByIndex(targetPhaseIndex) ?: return blocked("alert_invalid_phase")

        if (targetPhaseIndex <= currentPhase.index) return allowed

        val roadsEmpty = featureStore.getFeaturesByPhase(Phases.ROADS_KEY).isEmpty()
        val entrancesEmpty = featureStore.getFeaturesByPhase(Phases.HOUSE_ENTRANCES_KEY).isEmpty()
        return when (currentPhase.key) {
            Phases.ROADS_KEY -> if (roadsEmpty) blocked("alert_at_least_one_road") else allowed
            Phases.HOUSE_ENTRANCES_KEY -> if (entrancesEmpty) blocked("alert_at_least_one_entrance") else allowed
            else -> allowed
        }
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
        val phase = Phases.getByIndex(prevIndex) ?: return null
        featureStore.setCurrentPhase(phase)
        return phase
    }

    /**
     * Try to go to next phase - validates requirements first
     * @return PhaseDefinition if allowed, null if blocked
     */
    fun goNext(): PhaseDefinition? {
        val nextIndex = getNextPhaseIndex() ?: return null
        val phase = navigateTo(nextIndex) ?: return null
        featureStore.setCurrentPhase(phase)
        return phase
    }
}
