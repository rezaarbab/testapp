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

    @Test
    fun generate_noHugeOvershoot() {
        val need = 8000
        val text = StoryGenerator.generate(need, false)
        assertTrue(StegoEngine.capacityBits(text, false) >= need)
        assertTrue("carrier too long: ${text.length}", text.length < 120_000)
    }

    @Test
    fun generate_largePayload_carrierFits() {
        val need = StegoEngine.neededBits(StegoEngine.plainTextSize(20_000))
        val text = StoryGenerator.generate(need, true)
        assertTrue("robust capacity short: ${StegoEngine.capacityBits(text, true)} < $need",
            StegoEngine.capacityBits(text, true) >= need)
    }
}
