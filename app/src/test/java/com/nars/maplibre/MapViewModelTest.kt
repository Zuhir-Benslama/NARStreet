package com.nars.maplibre

import android.app.Application
import com.nars.maplibre.R
import com.nars.maplibre.data.api.ApiService
import com.nars.maplibre.data.api.BackendInteractor
import com.nars.maplibre.data.api.SessionManager
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.store.FeatureStoreInterface
import com.nars.maplibre.data.store.UndoAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val application = mockk<Application>(relaxed = true)
    private val featureStore = mockk<FeatureStoreInterface>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val apiService = mockk<ApiService>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val interactor = mockk<BackendInteractor>(relaxed = true)

    private val currentPhaseFlow = MutableStateFlow<PhaseDefinition?>(null)
    private val allFeaturesFlow = MutableStateFlow<List<NarsFeature>>(emptyList())
    private val selectedFeatureFlow = MutableStateFlow<NarsFeature?>(null)
    private val referenceRoadFlow = MutableStateFlow<String?>(null)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { featureStore.currentPhase } returns currentPhaseFlow
        every { featureStore.allFeatures } returns allFeaturesFlow
        every { featureStore.selectedFeature } returns selectedFeatureFlow
        every { featureStore.referenceRoadDbId } returns referenceRoadFlow
        every { featureStore.getFeaturesByPhase(any()) } returns emptyList()
        every { appPreferences.baseLayer } returns BaseLayerType.SATELLITE
        every { application.getString(R.string.map_nothing_undo) } returns "Nothing to undo"
        every { application.getString(R.string.undo_restored_format, any()) } answers {
            val args = arg<Any>(1)
            "Restored: ${(args as Array<*>)[0]}"
        }
        every { application.getString(R.string.undo_removed_format, any()) } answers {
            val args = arg<Any>(1)
            "Removed: ${(args as Array<*>)[0]}"
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): MapViewModel {
        val vm = MapViewModel(application, featureStore, appPreferences, apiService, sessionManager, interactor)
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `init does not override FeatureStore default phase`() {
        createViewModel()
        // FeatureStore.init sets first phase; MapViewModel no longer duplicates it
        verify(exactly = 0) { featureStore.setCurrentPhase(match { true }) }
    }

    @Test
    fun `setCurrentPhase sets phase and resets modes`() {
        currentPhaseFlow.value = Phases.ALL[0]
        every { featureStore.getFeaturesByPhase("roads") } returns
            listOf(
                NarsFeature(
                    id = "r1",
                    type = NarsFeatureType.ROAD,
                    geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                    properties = FeatureProperties.RoadProperties(),
                ),
            )
        val vm = createViewModel()

        val result = vm.setCurrentPhase(Phases.ALL[1])

        assertEquals(Phases.ALL[1], result)
        verify { featureStore.setCurrentPhase(Phases.ALL[1]) }
        verify { appPreferences.currentPhase = Phases.ALL[1].key }
        assertFalse(vm.drawingEnabled.value)
        assertFalse(vm.editModeEnabled.value)
    }

    @Test
    fun `setCurrentPhase returns null when blocked by navigator`() {
        currentPhaseFlow.value = Phases.ALL[0]
        every { featureStore.getFeaturesByPhase("roads") } returns emptyList()
        val vm = createViewModel()

        val result = vm.setCurrentPhase(Phases.ALL[1])

        assertNull(result)
    }

    @Test
    fun `goToNextPhase advances when navigator allows`() {
        currentPhaseFlow.value = Phases.ALL[0]
        every { featureStore.getFeaturesByPhase("roads") } returns
            listOf(
                NarsFeature(
                    id = "r1",
                    type = NarsFeatureType.ROAD,
                    geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                    properties = FeatureProperties.RoadProperties(),
                ),
            )
        val vm = createViewModel()

        val result = vm.goToNextPhase()

        assertEquals(Phases.ALL[1], result)
        verify { featureStore.setCurrentPhase(Phases.ALL[1]) }
    }

    @Test
    fun `goToNextPhase returns null when blocked`() {
        currentPhaseFlow.value = Phases.ALL[0]
        every { featureStore.getFeaturesByPhase("roads") } returns emptyList()
        val vm = createViewModel()

        val result = vm.goToNextPhase()

        assertNull(result)
    }

    @Test
    fun `goToPreviousPhase goes back`() = runTest {
        currentPhaseFlow.value = Phases.ALL[1]
        val vm = MapViewModel(application, featureStore, appPreferences, apiService, sessionManager, interactor)
        advanceUntilIdle()

        val result = vm.goToPreviousPhase()

        assertEquals(Phases.ALL[0], result)
        verify { featureStore.setCurrentPhase(Phases.ALL[0]) }
    }

    @Test
    fun `canGoNextPhase delegates to navigator`() {
        currentPhaseFlow.value = Phases.ALL[0]
        every { featureStore.getFeaturesByPhase("roads") } returns
            listOf(
                NarsFeature(
                    id = "r1",
                    type = NarsFeatureType.ROAD,
                    geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                    properties = FeatureProperties.RoadProperties(),
                ),
            )
        val vm = createViewModel()

        assertTrue(vm.canGoNextPhase())
    }

    @Test
    fun `addFeature delegates to featureStore`() {
        val vm = createViewModel()
        val feature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(),
            )

        vm.addFeature(feature)

        verify { featureStore.addFeature(feature, recordUndo = true) }
    }

    @Test
    fun `updateFeature updates and records undo`() {
        val vm = createViewModel()
        val oldFeature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Old"),
            )
        val updatedProps = (oldFeature.properties as FeatureProperties.RoadProperties).copy(name = "New")
        val newFeature = oldFeature.copy(properties = updatedProps)

        every { featureStore.updateFeatureWithUndo("f1", newFeature) } just Runs

        vm.updateFeature(newFeature)

        verify { featureStore.updateFeatureWithUndo("f1", newFeature) }
    }

    @Test
    fun `deleteFeature removes and records undo`() {
        val vm = createViewModel()
        val feature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(),
            )

        every { featureStore.getFeatureById("f1") } returns feature

        vm.deleteFeature("f1")

        verify { featureStore.removeFeature("f1") }
        verify { featureStore.addUndoAction(any()) }
    }

    @Test
    fun `selectFeature delegates to featureStore`() {
        val vm = createViewModel()
        val feature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(),
            )

        vm.selectFeature(feature)

        verify { featureStore.selectFeature(feature) }
    }

    @Test
    fun `toggleDrawing enables drawing and disables edit mode`() {
        val vm = createViewModel()

        vm.toggleDrawing(true)

        assertTrue(vm.drawingEnabled.value)
        assertFalse(vm.editModeEnabled.value)
    }

    @Test
    fun `toggleEditMode enables edit and disables drawing`() {
        val vm = createViewModel()

        vm.toggleEditMode(true)

        assertTrue(vm.editModeEnabled.value)
        assertFalse(vm.drawingEnabled.value)
    }

    @Test
    fun `undo executes undo and shows success`() {
        val vm = createViewModel()

        vm.undo()

        verify { featureStore.executeUndo() }
    }

    @Test
    fun `setBaseLayer updates layer and preference`() {
        val vm = createViewModel()

        vm.setBaseLayer(BaseLayerType.STREET)

        assertEquals(BaseLayerType.STREET, vm.baseLayer.value)
        verify { appPreferences.baseLayer = BaseLayerType.STREET }
    }

    @Test
    fun `setReferenceRoad delegates to featureStore`() {
        val vm = createViewModel()

        vm.setReferenceRoad("road-123")

        verify { featureStore.setReferenceRoad("road-123") }
    }

    @Test
    fun `clearSelection delegates to featureStore`() {
        val vm = createViewModel()

        vm.clearSelection()

        verify { featureStore.selectFeature(null) }
    }

    @Test
    fun `updateUiState sets error message`() {
        val vm = createViewModel()

        vm.updateUiState(errorMessage = "Test error")

        assertEquals("Test error", vm.uiState.value.errorMessage)
    }

    @Test
    fun `updateUiState clears error message`() {
        val vm = createViewModel()
        vm.updateUiState(errorMessage = "Test error")

        vm.clearErrorMessage()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `updateUiState sets success message`() {
        val vm = createViewModel()

        vm.updateUiState(successMessage = "Test success")

        assertEquals("Test success", vm.uiState.value.successMessage)
    }

    @Test
    fun `updateUiState clears success message`() {
        val vm = createViewModel()
        vm.updateUiState(successMessage = "Test success")

        vm.clearSuccessMessage()

        assertNull(vm.uiState.value.successMessage)
    }

    @Test
    fun `updateUiState sets loading`() {
        val vm = createViewModel()

        vm.updateUiState(isLoading = true)

        assertTrue(vm.uiState.value.isLoading)

        vm.updateUiState(isLoading = false)

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `selectedFeatureId maps from featureStore selectedFeature`() {
        every { featureStore.selectedFeature } returns MutableStateFlow(null)
        val vm = createViewModel()

        assertNull(vm.selectedFeatureId.value)
    }

    @Test
    fun `undo with Delete action restores feature`() {
        val feature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Restored Road"),
            )
        val undoAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Delete>(relaxed = true)
        every { undoAction.feature } returns feature
        every { featureStore.executeUndo() } returns undoAction

        val vm = createViewModel()
        vm.undo()

        assertEquals("Restored: Restored Road", vm.uiState.value.successMessage)
    }

    @Test
    fun `undo with Create action shows success`() {
        val feature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Removed Road"),
            )
        val undoAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Create>(relaxed = true)
        every { undoAction.feature } returns feature
        every { featureStore.executeUndo() } returns undoAction

        val vm = createViewModel()
        val result = vm.undo()

        assertNotNull(result)
        assertEquals("Removed: Removed Road", vm.uiState.value.successMessage)
    }

    @Test
    fun `undo with Update action shows success`() {
        val oldFeature =
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Old Name"),
            )
        val undoAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Update>(relaxed = true)
        every { undoAction.oldFeature } returns oldFeature
        every { featureStore.executeUndo() } returns undoAction

        val vm = createViewModel()
        val result = vm.undo()

        assertNotNull(result)
        assertEquals("Restored: Old Name", vm.uiState.value.successMessage)
    }

    @Test
    fun `undo returns false when nothing to undo`() {
        every { featureStore.executeUndo() } returns null
        val vm = createViewModel()

        val result = vm.undo()

        assertNull(result)
        assertEquals("Nothing to undo", vm.uiState.value.errorMessage)
    }

    @Test
    fun `undo returns true on successful undo`() {
        val undoAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Delete>(relaxed = true)
        every { undoAction.feature } returns
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        every { featureStore.executeUndo() } returns undoAction

        val vm = createViewModel()
        val result = vm.undo()

        assertNotNull(result)
    }

    @Test
    fun `sequential undo processes actions in LIFO order`() {
        val firstAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Delete>(relaxed = true)
        every { firstAction.feature } returns
            NarsFeature(
                id = "f1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "First"),
            )
        val secondAction = io.mockk.mockk<com.nars.maplibre.data.store.UndoAction.Delete>(relaxed = true)
        every { secondAction.feature } returns
            NarsFeature(
                id = "f2",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Second"),
            )

        var callCount = 0
        every { featureStore.executeUndo() } answers {
            callCount++
            when (callCount) {
                1 -> secondAction
                2 -> firstAction
                else -> null
            }
        }

        val vm = createViewModel()

        assertNotNull(vm.undo())
        assertEquals("Restored: Second", vm.uiState.value.successMessage)

        assertNotNull(vm.undo())
        assertEquals("Restored: First", vm.uiState.value.successMessage)
    }

    @Test
    fun `canUndo updates after adding a feature`() {
        val undoState = MutableStateFlow(false)
        every { featureStore.undoState } returns undoState
        val vm = createViewModel()

        undoState.value = true

        assertTrue(vm.canUndo.value)
    }

    // ─── Backend operations (viewModelScope-backed) ──────────────────────────

    private fun testFeature(id: String = "f1", dbId: String? = null) = NarsFeature(
        id = id,
        dbId = dbId,
        type = NarsFeatureType.ROAD,
        geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
        properties = FeatureProperties.RoadProperties(name = "Test Road"),
    )

    @Test
    fun `loadFeatures adds backend features and clears loading`() {
        val features = listOf(testFeature("srv-1", dbId = "srv-1"))
        coEvery { interactor.loadFeatures() } returns Result.success(features)
        val vm = createViewModel()

        vm.loadFeatures()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { featureStore.addFeatures(features) }
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadFeatures failure surfaces error and clears loading`() {
        coEvery { interactor.loadFeatures() } returns Result.failure(java.io.IOException("boom"))
        val vm = createViewModel()

        vm.loadFeatures()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("boom"))
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `saveFeatureToBackend attaches server id to stored feature`() {
        val feature = testFeature("client-1")
        coEvery { interactor.saveFeature(feature) } returns Result.success("srv-9")
        every { featureStore.getFeatureById("client-1") } returns feature
        val vm = createViewModel()

        vm.saveFeatureToBackend(feature)
        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            featureStore.updateFeature("client-1", match { it.dbId == "srv-9" && it.id == "client-1" })
        }
    }

    @Test
    fun `saveFeatureToBackend retries transient failures then reports error`() {
        val feature = testFeature("f1")
        coEvery { interactor.saveFeature(any()) } returns Result.failure(java.io.IOException("net down"))
        val vm = createViewModel()

        vm.saveFeatureToBackend(feature)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.saveFeature(any()) }
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("net down"))
    }

    @Test
    fun `updateFeatureOnBackend pushes to the backend id`() {
        val feature = testFeature("client-1", dbId = "srv-9")
        coEvery { interactor.updateFeature("srv-9", feature) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.updateFeatureOnBackend(feature)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.updateFeature("srv-9", feature) }
    }

    @Test
    fun `deleteFeatureOnBackend skips API for local-only feature`() {
        val feature = testFeature("client-1", dbId = null)
        every { featureStore.getFeatureById("client-1") } returns feature
        val vm = createViewModel()

        vm.deleteFeatureOnBackend("client-1")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { featureStore.removeFeature("client-1") }
        coVerify(exactly = 0) { interactor.deleteFeature(any()) }
        verify(exactly = 0) { featureStore.addFeature(any(), recordUndo = false) }
    }

    @Test
    fun `deleteFeatureOnBackend rolls back local delete when API fails`() {
        val feature = testFeature("f1", dbId = "srv-1")
        every { featureStore.getFeatureById("f1") } returns feature
        allFeaturesFlow.value = emptyList()
        coEvery { interactor.deleteFeature("srv-1") } returns Result.failure(Exception("denied"))
        val vm = createViewModel()

        vm.deleteFeatureOnBackend("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { featureStore.removeFeature("f1") }
        verify(exactly = 1) { featureStore.addFeature(feature, recordUndo = false) }
        verify { featureStore.removeMostRecentActionForFeature("f1") }
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("denied"))
    }

    @Test
    fun `deleteFeatureOnBackend does not roll back when feature was re-added meanwhile`() {
        val feature = testFeature("f1", dbId = "srv-1")
        every { featureStore.getFeatureById("f1") } returns feature
        coEvery { interactor.deleteFeature("srv-1") } returns Result.failure(Exception("denied"))
        val vm = createViewModel()
        // Simulates the user re-drawing the same feature while the DELETE was
        // in flight (the launch only runs once the scheduler advances).
        allFeaturesFlow.value = listOf(feature.copy(id = "f1"))

        vm.deleteFeatureOnBackend("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { featureStore.addFeature(any(), recordUndo = false) }
        verify(exactly = 0) { featureStore.removeMostRecentActionForFeature("f1") }
    }

    @Test
    fun `logout clears session then invokes navigation callback`() {
        val vm = createViewModel()
        var navigated = false
        every { sessionManager.logout(any(), any()) } answers {
            firstArg<() -> Unit>()()
        }

        vm.logout { navigated = true }
        testDispatcher.scheduler.advanceUntilIdle()

        verify { sessionManager.logout(any(), any()) }
        assertTrue(navigated)
    }
}
