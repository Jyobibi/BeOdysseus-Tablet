package com.beodysseus.poseprototype.result

import com.beodysseus.poseprototype.feedback.PostureFeedbackResult
import com.beodysseus.poseprototype.feedback.PostureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostureDataCollectorTest {

    @Test
    fun collectStageDataTest() {
        val collector = PostureDataCollector()

        // 첫 번째 프레임: 모두 정상
        collector.addFrame(
            1,
            PostureFeedbackResult(
                bodyStatus = PostureStatus.NORMAL,
                bowArmStatus = PostureStatus.NORMAL,
                drawArmStatus = PostureStatus.NORMAL
            )
        )

        // 두 번째 프레임: 활팔만 주의
        collector.addFrame(
            1,
            PostureFeedbackResult(
                bodyStatus = PostureStatus.NORMAL,
                bowArmStatus = PostureStatus.WARNING,
                drawArmStatus = PostureStatus.NORMAL
            )
        )

        val data = collector.getStageData(1)!!

        assertEquals(2, data.totalFrames)
        assertEquals(2, data.bodyNormalFrames)
        assertEquals(1, data.bowArmNormalFrames)
        assertEquals(2, data.drawArmNormalFrames)
    }

    @Test
    fun nullFrameIsIgnoredTest() {
        val collector = PostureDataCollector()

        collector.addFrame(
            1,
            PostureFeedbackResult(
                bodyStatus = null,
                bowArmStatus = PostureStatus.NORMAL,
                drawArmStatus = PostureStatus.NORMAL
            )
        )

        assertNull(
            collector.getStageData(1)
        )
    }

    @Test
    fun stageDataIsSeparatedTest() {
        val collector = PostureDataCollector()

        collector.addFrame(
            1,
            PostureFeedbackResult(
                PostureStatus.NORMAL,
                PostureStatus.NORMAL,
                PostureStatus.NORMAL
            )
        )

        collector.addFrame(
            2,
            PostureFeedbackResult(
                PostureStatus.WARNING,
                PostureStatus.NORMAL,
                PostureStatus.WARNING
            )
        )

        assertEquals(
            1,
            collector.getStageData(1)!!.bodyNormalFrames
        )

        assertEquals(
            0,
            collector.getStageData(2)!!.bodyNormalFrames
        )
    }
}