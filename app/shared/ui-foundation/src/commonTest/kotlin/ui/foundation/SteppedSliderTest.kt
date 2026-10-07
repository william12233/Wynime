package com.wynime.app.ui.foundation

import kotlin.test.Test
import kotlin.test.assertEquals

class SteppedSliderTest {
    private val defaultRange = 0.5f..2.5f

    @Test
    fun `quantize rounds to nearest 0_25 step`() {
        assertEquals(1.25f, quantizeSliderValue(1.344f, defaultRange))
        assertEquals(1.25f, quantizeSliderValue(1.236f, defaultRange))
        assertEquals(1.5f, quantizeSliderValue(1.38f, defaultRange))
    }

    @Test
    fun `quantize keeps values already on grid`() {
        assertEquals(1.5f, quantizeSliderValue(1.5f, defaultRange))
        assertEquals(2f, quantizeSliderValue(2f, defaultRange))
    }

    @Test
    fun `quantize clamps to range`() {
        assertEquals(0.5f, quantizeSliderValue(0.1f, defaultRange))
        assertEquals(2.5f, quantizeSliderValue(5f, defaultRange))
    }

    @Test
    fun `quantize handles non finite values and slider steps match the range`() {
        assertEquals(0.5f, quantizeSliderValue(Float.NaN, defaultRange))
        assertEquals(7, sliderStepsInRange(defaultRange))
        assertEquals(0, sliderStepsInRange(1f..1f))
    }

    @Test
    fun `quantize snaps a two endpoint range`() {
        val range = 1.5f..1.75f

        assertEquals(1.75f, quantizeSliderValue(1.69f, range))
        assertEquals(1.5f, quantizeSliderValue(1.56f, range))
    }
}
