package com.beodysseus.poseprototype.feedback

enum class PostureStatus {
    NORMAL,
    WARNING
}

data class PostureFeedbackResult(
    val bodyStatus: PostureStatus?,
    val bowArmStatus: PostureStatus?
)

class PostureFeedbackEvaluator {

    companion object {
        // 임시 기준값 - 실제 측정 후 조정
        private const val BODY_LEAN_THRESHOLD = 10f

        // 긴팔 착용 시 키포인트 오차를 고려해 기준 완화
        private const val BOW_ARM_THRESHOLD = 20f
    }

    fun evaluate(
        bodyLeanDegree: Float?,
        bowArmStraightnessErrorDegree: Float?
    ): PostureFeedbackResult {

        return PostureFeedbackResult(
            bodyStatus = evaluateValue(
                bodyLeanDegree,
                BODY_LEAN_THRESHOLD
            ),
            bowArmStatus = evaluateValue(
                bowArmStraightnessErrorDegree,
                BOW_ARM_THRESHOLD
            )
        )
    }

    private fun evaluateValue(
        value: Float?,
        threshold: Float
    ): PostureStatus? {

        if (value == null) {
            return null
        }

        return if (value <= threshold) {
            PostureStatus.NORMAL
        } else {
            PostureStatus.WARNING
        }
    }
}