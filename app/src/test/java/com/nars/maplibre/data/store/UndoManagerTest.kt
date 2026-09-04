package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PointGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoManagerTest {
    private lateinit var undoManager: UndoManager

    private fun createRoad(id: String): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.ROAD,
        geometry = LineStringGeometry(coordinates = listOf(3.0, 36.0, 3.01, 36.0)),
        properties = FeatureProperties.RoadProperties(name = "Road $id"),
    )

    @Before
    fun setUp() {
        undoManager = UndoManager()
    }

    @Test
    fun `popUndoAction returns null when empty`() {
        assertNull(undoManager.popUndoAction())
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canUndoState.value)
    }

    @Test
    fun `popUndoAction is LIFO`() {
        undoManager.addUndoAction(UndoAction.Create(createRoad("r1")))
        undoManager.addUndoAction(UndoAction.Create(createRoad("r2")))

        val first = undoManager.popUndoAction() as UndoAction.Create
        assertEquals("r2", first.feature.id)
        val second = undoManager.popUndoAction() as UndoAction.Create
        assertEquals("r1", second.feature.id)
        assertFalse(undoManager.canUndo)
    }

    @Test
    fun `popUndoAction updates canUndoState`() {
        undoManager.addUndoAction(UndoAction.Create(createRoad("r1")))
        assertTrue(undoManager.canUndo)
        assertTrue(undoManager.canUndoState.value)

        undoManager.popUndoAction()
        assertFalse(undoManager.canUndo)
        assertFalse(undoManager.canUndoState.value)
    }

    @Test
    fun `stack is capped at 50 actions`() {
        for (i in 0 until 60) {
            undoManager.addUndoAction(UndoAction.Create(createRoad("r$i")))
        }

        var popped = 0
        var action = undoManager.popUndoAction()
        while (action != null) {
            popped++
            action = undoManager.popUndoAction()
        }
        assertEquals(50, popped)
    }

    @Test
    fun `consecutive updates of the same feature collapse to a single action`() {
        val original = createRoad("r1")
        val mid = original.copy(geometry = PointGeometry(coordinates = listOf(1.0, 1.0)))
        val final = original.copy(geometry = PointGeometry(coordinates = listOf(2.0, 2.0)))
        undoManager.addUndoAction(UndoAction.Update(oldFeature = original, newFeature = mid))
        undoManager.addUndoAction(UndoAction.Update(oldFeature = mid, newFeature = final))

        val collapsed = undoManager.popUndoAction() as UndoAction.Update
        assertEquals(original, collapsed.oldFeature)
        assertEquals(final, collapsed.newFeature)
        // collapsed to a single action — nothing remains
        assertNull(undoManager.popUndoAction())
    }

    @Test
    fun `updates of different features are not collapsed`() {
        val a = createRoad("r1")
        val b = createRoad("r2")
        undoManager.addUndoAction(
            UndoAction.Update(oldFeature = a, newFeature = a.copy(properties = roadNamed(a, "A"))),
        )
        undoManager.addUndoAction(
            UndoAction.Update(oldFeature = b, newFeature = b.copy(properties = roadNamed(b, "B"))),
        )

        val second = undoManager.popUndoAction() as UndoAction.Update
        val first = undoManager.popUndoAction() as UndoAction.Update
        assertEquals("r2", second.newFeature.id)
        assertEquals("r1", first.newFeature.id)
        assertNull(undoManager.popUndoAction())
    }

    @Test
    fun `clear empties the stack`() {
        undoManager.addUndoAction(UndoAction.Create(createRoad("r1")))
        undoManager.addUndoAction(UndoAction.Create(createRoad("r2")))

        undoManager.clear()

        assertFalse(undoManager.canUndo)
        assertNull(undoManager.popUndoAction())
    }

    @Test
    fun `removeMostRecentActionForFeature removes only the most recent Delete for the feature`() {
        undoManager.addUndoAction(UndoAction.Update(oldFeature = createRoad("r1"), newFeature = createRoad("r1a")))
        undoManager.addUndoAction(UndoAction.Delete(createRoad("r1")))
        undoManager.addUndoAction(UndoAction.Delete(createRoad("r1")))

        val removed = undoManager.removeMostRecentActionForFeature("r1")
        assertTrue(removed is UndoAction.Delete)
        // one Delete remains for the feature
        assertTrue(undoManager.removeMostRecentActionForFeature("r1") is UndoAction.Delete)
        // non-Delete actions are never dropped
        assertNull(undoManager.removeMostRecentActionForFeature("r1"))
    }

    @Test
    fun `removeMostRecentActionForFeature returns null when feature has no Delete`() {
        undoManager.addUndoAction(UndoAction.Create(createRoad("r1")))
        assertNull(undoManager.removeMostRecentActionForFeature("r1"))
    }

    private fun roadNamed(base: NarsFeature, name: String): FeatureProperties.RoadProperties =
        (base.properties as FeatureProperties.RoadProperties).copy(name = name)
}
