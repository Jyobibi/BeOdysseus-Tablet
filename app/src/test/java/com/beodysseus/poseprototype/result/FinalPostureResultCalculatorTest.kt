package com.beodysseus.poseprototype.result

import com.beodysseus.poseprototype.feedback.PostureFeedbackResult
import com.beodysseus.poseprototype.feedback.PostureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinalPostureResultCalculatorTest {

    @Test
    fun finalResultCalculationTest() {

        val collector = PostureDataCollector()

        // Stage 1: 모두 정상
        collector.addFrame(
            1,
            PostureFeedbackResult(
                PostureStatus.NORMAL,
                PostureStatus.NORMAL,
                PostureStatus.NORMAL
            )
        )

        // Stage 2: 활팔만 주의
        collector.addFrame(
            2,
            PostureFeedbackResult(
                PostureStatus.NORMAL,
                PostureStatus.WARNING,
                PostureStatus.NORMAL
            )
        )

        // Stage 3: 당김팔만 주의
        collector.addFrame(
            3,
            PostureFeedbackResult(
                PostureStatus.NORMAL,
                PostureStatus.NORMAL,
                PostureStatus.WARNING
            )
        )

        val calculator =
            FinalPostureResultCalculator()

        val result =
            calculator.calculate(collector)!!

        assertEquals(77, result.overallScore)

        assertEquals(100, result.bodyScore)
        assertEquals(66, result.bowArmScore)
        assertEquals(66, result.drawArmScore)

        assertEquals(100, result.stage1Score)
        assertEquals(66, result.stage2Score)
        assertEquals(66, result.stage3Score)
    }

    @Test
    fun noDataTest() {

        val collector =
            PostureDataCollector()

        val calculator =
            FinalPostureResultCalculator()

        val result =
            calculator.calculate(collector)

        assertNull(result)
    }
}