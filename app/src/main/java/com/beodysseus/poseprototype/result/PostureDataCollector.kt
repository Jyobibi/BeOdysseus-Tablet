package com.beodysseus.poseprototype.result

import com.beodysseus.poseprototype.feedback.PostureFeedbackResult
import com.beodysseus.poseprototype.feedback.PostureStatus

data class StagePostureData(
    var totalFrames: Int = 0,
    var bodyNormalFrames: Int = 0,
    var bowArmNormalFrames: Int = 0,
    var drawArmNormalFrames: Int = 0
)

class PostureDataCollector {

    private val stageData =
        mutableMapOf<Int, StagePostureData>()

    fun addFrame(
        stageNumber: Int,
        feedback: PostureFeedbackResult
    ) {

        if (stageNumber !in 1..3) {
            return
        }

        // null이 있는 프레임은 자세 인식이 불안정한 것으로 보고 제외
        if (
            feedback.bodyStatus == null ||
            feedback.bowArmStatus == null ||
            feedback.drawArmStatus == null
        ) {
            return
        }

        val data =
            stageData.getOrPut(stageNumber) {
                StagePostureData()
            }

        data.totalFrames++

        if (feedback.bodyStatus == PostureStatus.NORMAL) {
            data.bodyNormalFrames++
        }

        if (feedback.bowArmStatus == PostureStatus.NORMAL) {
            data.bowArmNormalFrames++
        }

        if (feedback.drawArmStatus == PostureStatus.NORMAL) {
            data.drawArmNormalFrames++
        }
    }

    fun getStageData(
        stageNumber: Int
    ): StagePostureData? {
        return stageData[stageNumber]
    }

    fun reset() {
        stageData.clear()
    }
}