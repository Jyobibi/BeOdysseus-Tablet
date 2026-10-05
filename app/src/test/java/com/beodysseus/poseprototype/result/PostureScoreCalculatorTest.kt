package com.beodysseus.poseprototype.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostureScoreCalculatorTest {

    private val calculator =
        PostureScoreCalculator()

    @Test
    fun scoreCalculationTest() {

        val data = StagePostureData(
            totalFrames = 100,
            bodyNormalFrames = 90,
            bowArmNormalFrames = 82,
            drawArmNormalFrames = 84
        )

        val score =
            calculator.calculate(data)!!

        assertEquals(90, score.bodyScore)
        assertEquals(82, score.bowArmScore)
        assertEquals(84, score.drawArmScore)
        assertEquals(85, score.overallScore)
    }

    @Test
    fun noDataTest() {

        val score =
            calculator.calculate(null)

        assertNull(score)
    }

    @Test
    fun zeroFrameTest() {

        val data =
            StagePostureData(
                totalFrames = 0
            )

        val score =
            calculator.calculate(data)

        assertNull(score)
    }
}