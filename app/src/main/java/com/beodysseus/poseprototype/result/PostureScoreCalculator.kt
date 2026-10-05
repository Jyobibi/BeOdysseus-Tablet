package com.beodysseus.poseprototype.result

data class PostureScore(
    val bodyScore: Int,
    val bowArmScore: Int,
    val drawArmScore: Int,
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

        val drawArmScore =
            calculatePercentage(
                data.drawArmNormalFrames,
                data.totalFrames
            )

        // 세 자세 항목의 평균을 Stage 종합 안정도로 사용
        val overallScore =
            (bodyScore + bowArmScore + drawArmScore) / 3

        return PostureScore(
            bodyScore = bodyScore,
            bowArmScore = bowArmScore,
            drawArmScore = drawArmScore,
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