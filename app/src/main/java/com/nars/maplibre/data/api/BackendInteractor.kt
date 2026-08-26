package com.nars.maplibre.data.api

import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.utils.retryOnTransientFailure

/**
 * Encapsulates backend API calls with automatic transient-failure retry.
 * Extracted from [com.nars.maplibre.MapViewModel] to keep the ViewModel
 * focused on UI state while network orchestration lives here.
 */
class BackendInteractor(private val apiService: ApiService) {

    suspend fun loadFeatures(): Result<List<NarsFeature>> = retryOnTransientFailure { apiService.loadFeatures() }

    suspend fun saveFeature(feature: NarsFeature): Result<String> =
        retryOnTransientFailure { apiService.saveFeature(feature) }

    suspend fun updateFeature(featureId: String, feature: NarsFeature): Result<Unit> =
        retryOnTransientFailure { apiService.updateFeature(featureId, feature) }

    suspend fun deleteFeature(featureId: String): Result<Unit> =
        retryOnTransientFailure { apiService.deleteFeature(featureId) }
}
