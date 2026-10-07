package com.wynime.app.ui.foundation.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrollHotKeysTest {
    @Test
    fun `direction selection ignores empty list`() {
        assertNull(calculateDirectionSelectionIndex(selectedItemIndex = -1, itemCount = 0, delta = 1))
    }

    @Test
    fun `direction selection starts at first item when nothing is selected`() {
        assertEquals(0, calculateDirectionSelectionIndex(selectedItemIndex = -1, itemCount = 3, delta = 1))
        assertEquals(0, calculateDirectionSelectionIndex(selectedItemIndex = -1, itemCount = 3, delta = -1))
    }

    @Test
    fun `direction selection does nothing at list boundaries`() {
        assertNull(calculateDirectionSelectionIndex(selectedItemIndex = 0, itemCount = 3, delta = -1))
        assertNull(calculateDirectionSelectionIndex(selectedItemIndex = 2, itemCount = 3, delta = 1))
    }

    @Test
    fun `direction selection recovers stale out of range selection`() {
        assertEquals(2, calculateDirectionSelectionIndex(selectedItemIndex = 3, itemCount = 3, delta = 1))
        assertEquals(2, calculateDirectionSelectionIndex(selectedItemIndex = 3, itemCount = 3, delta = -1))
    }
}
