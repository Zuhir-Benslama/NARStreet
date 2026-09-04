package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun `formatDecimal pads to the requested digits`() {
        assertEquals("3.14", PI.formatDecimal(2))
        assertEquals("3.142", PI.formatDecimal(3))
    }

    @Test
    fun `formatDecimal rounds instead of truncating`() {
        assertEquals("4", THREE_AND_HALF.formatDecimal(0))
        assertEquals("3.4", THREE_AND_FOUR_TENTHS.formatDecimal(1))
    }

    @Test
    fun `formatDecimal with zero digits rounds to integer`() {
        assertEquals("3", 3.0.formatDecimal(0))
        assertEquals("5", 4.6.formatDecimal(0))
    }

    @Test
    fun `formatDecimal with negative value`() {
        assertEquals("-3.14", (-3.14159).formatDecimal(2))
    }

    @Test
    fun `formatDecimal with zero value`() {
        assertEquals("0.00", 0.0.formatDecimal(2))
    }

    @Test
    fun `formatDecimal with large number`() {
        assertEquals("1000000.00", 1000000.0.formatDecimal(2))
    }

    private companion object {
        const val PI = 3.14159
        const val THREE_AND_HALF = 3.5
        const val THREE_AND_FOUR_TENTHS = 3.4
    }
}
