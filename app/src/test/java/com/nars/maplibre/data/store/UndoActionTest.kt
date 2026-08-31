package com.nars.maplibre.data.store

import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoActionTest {

    private fun road(id: String): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.ROAD,
        geometry = LineStringGeometry(coordinates = listOf(3.0, 36.0, 3.01, 36.0)),
        properties = FeatureProperties.RoadProperties(name = "Road $id"),
    )

    // ─── Delete.references ─────────────────────────────────────────────────

    @Test
    fun `Delete references matching id`() {
        assertTrue(UndoAction.Delete(road("r1")).references("r1"))
    }

    @Test
    fun `Delete does not reference a different id`() {
        assertFalse(UndoAction.Delete(road("r1")).references("r2"))
    }

    // ─── Create.references ─────────────────────────────────────────────────

    @Test
    fun `Create references matching id`() {
        assertTrue(UndoAction.Create(road("r1")).references("r1"))
    }

    @Test
    fun `Create does not reference a different id`() {
        assertFalse(UndoAction.Create(road("r1")).references("r2"))
    }

    // ─── Update.references ─────────────────────────────────────────────────

    @Test
    fun `Update references the new feature id`() {
        val action = UndoAction.Update(oldFeature = road("r1"), newFeature = road("r2"))
        assertTrue(action.references("r2"))
    }

    @Test
    fun `Update references the old feature id`() {
        val action = UndoAction.Update(oldFeature = road("r1"), newFeature = road("r2"))
        assertTrue(action.references("r1"))
    }

    @Test
    fun `Update does not reference an unrelated id`() {
        val action = UndoAction.Update(oldFeature = road("r1"), newFeature = road("r2"))
        assertFalse(action.references("r3"))
    }

    @Test
    fun `Update references an id shared by both old and new`() {
        val shared = road("r1")
        val action = UndoAction.Update(oldFeature = shared, newFeature = shared)
        assertTrue(action.references("r1"))
    }
}
