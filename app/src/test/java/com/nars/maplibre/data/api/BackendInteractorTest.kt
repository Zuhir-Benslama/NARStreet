package com.nars.maplibre.data.api

import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BackendInteractorTest {
    private val apiService: ApiService = mockk()
    private lateinit var interactor: BackendInteractor

    private fun createFeature(id: String): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.ROAD,
        geometry = LineStringGeometry(coordinates = listOf(3.0, 36.0, 3.01, 36.0)),
        properties = FeatureProperties.RoadProperties(name = "Road $id"),
    )

    @Before
    fun setUp() {
        interactor = BackendInteractor(apiService)
    }

    @Test
    fun `loadFeatures delegates to the api service`() = runTest {
        val features = listOf(createFeature("r1"), createFeature("r2"))
        coEvery { apiService.loadFeatures() } returns Result.success(features)

        val result = interactor.loadFeatures()

        assertTrue(result.isSuccess)
        assertEquals(features, result.getOrNull())
        coVerify(exactly = 1) { apiService.loadFeatures() }
    }

    @Test
    fun `loadFeatures retries transient failures then succeeds`() = runTest {
        var calls = 0
        coEvery { apiService.loadFeatures() } answers {
            calls++
            if (calls < 3) Result.failure(IOException("flaky")) else Result.success(emptyList())
        }

        val result = interactor.loadFeatures()

        assertTrue(result.isSuccess)
        assertEquals(3, calls)
    }

    @Test
    fun `loadFeatures retries up to three times then surfaces the failure`() = runTest {
        coEvery { apiService.loadFeatures() } returns Result.failure(IOException("always down"))

        val result = interactor.loadFeatures()

        assertTrue(result.isFailure)
        coVerify(exactly = 3) { apiService.loadFeatures() }
    }

    @Test
    fun `loadFeatures does not retry non-transient failures`() = runTest {
        coEvery { apiService.loadFeatures() } returns Result.failure(IllegalStateException("auth died"))

        val result = interactor.loadFeatures()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 1) { apiService.loadFeatures() }
    }

    @Test
    fun `saveFeature delegates and returns the backend id`() = runTest {
        val feature = createFeature("r1")
        coEvery { apiService.saveFeature(feature) } returns Result.success("backend-id")

        val result = interactor.saveFeature(feature)

        assertTrue(result.isSuccess)
        assertEquals("backend-id", result.getOrNull())
    }

    @Test
    fun `updateFeature delegates and returns`() = runTest {
        val feature = createFeature("r1")
        coEvery { apiService.updateFeature("r1", feature) } returns Result.success(Unit)

        val result = interactor.updateFeature("r1", feature)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiService.updateFeature("r1", feature) }
    }

    @Test
    fun `deleteFeature delegates and returns`() = runTest {
        coEvery { apiService.deleteFeature("backend-id") } returns Result.success(Unit)

        val result = interactor.deleteFeature("backend-id")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiService.deleteFeature("backend-id") }
    }

    @Test
    fun `deleteFeature retries transient failures`() = runTest {
        var calls = 0
        coEvery { apiService.deleteFeature("backend-id") } answers {
            calls++
            if (calls < 2) Result.failure(IOException("flaky")) else Result.success(Unit)
        }

        val result = interactor.deleteFeature("backend-id")

        assertTrue(result.isSuccess)
        assertEquals(2, calls)
    }
}
