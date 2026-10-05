package com.beodysseus.poseprototype.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostureFeedbackEvaluatorTest {

    private val evaluator = PostureFeedbackEvaluator()

    @Test
    fun postureEvaluationTest() {
        val result = evaluator.evaluate(
            bodyLeanDegree = 5.2f,
            bowArmStraightnessErrorDegree = 4.8f,
            drawArmAlignmentErrorDegree = 17.3f
        )

        assertEquals(PostureStatus.NORMAL, result.bodyStatus)
        assertEquals(PostureStatus.NORMAL, result.bowArmStatus)
        assertEquals(PostureStatus.WARNING, result.drawArmStatus)
    }

    @Test
    fun nullValueTest() {
        val result = evaluator.evaluate(
            bodyLeanDegree = null,
            bowArmStraightnessErrorDegree = 4.8f,
            drawArmAlignmentErrorDegree = null
        )

        assertNull(result.bodyStatus)
        assertEquals(PostureStatus.NORMAL, result.bowArmStatus)
        assertNull(result.drawArmStatus)
    }
}