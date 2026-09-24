package com.stegatext.app

import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGeneratorTest {

    @Test
    fun generate_producesEnoughCapacity() {
        val text = StoryGenerator.generate(640, false)
        assertTrue(StegoEngine.capacityBits(text, false) >= 640)
    }

    @Test
    fun generate_robustHasSwaps() {
        val text = StoryGenerator.generate(1200, true)
        assertTrue(StegoEngine.capacityBits(text, true) >= 120)
    }
}
