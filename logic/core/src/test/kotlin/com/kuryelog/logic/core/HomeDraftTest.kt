package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

class HomeDraftTest {
    @Test fun acceptsCoordinateEdgesAndZero() {
        for ((lat, lon) in listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0))
            assertNotNull(HomeDraft.create(lat, lon))
    }
    @Test fun rejectsMissingNonFiniteAndOutOfRangeCoordinates() {
        for ((lat, lon) in listOf(null to 49.0, 40.0 to null, Double.NaN to 49.0,
            40.0 to Double.POSITIVE_INFINITY, -90.01 to 49.0, 40.0 to 180.01))
            assertNull(HomeDraft.create(lat, lon))
    }
    @Test fun rejectsInvalidAccuracyButAllowsUnknownAndZero() {
        for (accuracy in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0))
            assertNull(HomeDraft.create(40.0, 49.0, accuracy))
        assertNotNull(HomeDraft.create(40.0, 49.0))
        assertNotNull(HomeDraft.create(40.0, 49.0, 0.0))
    }
    @Test fun warnsOnlyAbove25Meters() {
        assertFalse(HomeDraft.create(40.0, 49.0, 25.0)!!.needsWarning)
        assertTrue(HomeDraft.create(40.0, 49.0, 25.01)!!.needsWarning)
        assertFalse(HomeDraft.create(40.0, 49.0)!!.needsWarning)
    }
    @Test fun selectingOrDiscardingADraftNeverChangesSavedHome() {
        var saved = HomeRepository.Saved("40.0", "49.0", 123L)
        var writes = 0
        val repository = HomeRepository(object : HomeRepository.Storage {
            override fun read() = saved
            override fun write(value: HomeRepository.Saved) { saved = value; writes++ }
        })
        val draft = HomeDraft.create(41.0, 50.0, 10.0)!!
        assertEquals(40.0, repository.point()!!.lat, 0.0)
        assertEquals(123L, repository.point()!!.updatedAt)
        assertEquals(0, writes)
        // Only the explicit Save action writes the selected coordinates.
        repository.set(draft.lat, draft.lon, 200L)
        assertEquals(41.0, repository.point()!!.lat, 0.0)
        assertEquals(1, writes)
    }
}
