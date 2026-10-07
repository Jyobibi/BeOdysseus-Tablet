package com.beodysseus.poseprototype.result

data class PostureScore(
    val bodyScore: Int,
    val bowArmScore: Int,
    val overallScore: Int
)

class PostureScoreCalculator {

    fun calculate(
        data: StagePostureData?
    ): PostureScore? {

        // 측정 데이터가 없으면 점수 계산 불가
        if (data == null || data.totalFrames == 0) {
            return null
        }

        val bodyScore =
            calculatePercentage(
                data.bodyNormalFrames,
                data.totalFrames
            )

        val bowArmScore =
            calculatePercentage(
                data.bowArmNormalFrames,
                data.totalFrames
            )

        // 상체 + 활팔 두 자세 항목의 평균
        val overallScore =
            (bodyScore + bowArmScore) / 2

        return PostureScore(
            bodyScore = bodyScore,
            bowArmScore = bowArmScore,
            overallScore = overallScore
        )
    }

    private fun calculatePercentage(
        normalFrames: Int,
        totalFrames: Int
    ): Int {

        return (
                normalFrames.toFloat() /
                        totalFrames.toFloat() *
                        100
                ).toInt()
    }
}