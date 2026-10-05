package com.beodysseus.poseprototype.result

data class FinalPostureResult(
    val overallScore: Int,
    val bodyScore: Int,
    val bowArmScore: Int,
    val drawArmScore: Int,
    val stage1Score: Int?,
    val stage2Score: Int?,
    val stage3Score: Int?
)

class FinalPostureResultCalculator {

    fun calculate(
        collector: PostureDataCollector
    ): FinalPostureResult? {

        val stage1 =
            collector.getStageData(1)

        val stage2 =
            collector.getStageData(2)

        val stage3 =
            collector.getStageData(3)

        val validStages =
            listOfNotNull(
                stage1,
                stage2,
                stage3
            )

        // 유효한 Stage 데이터가 하나도 없으면 결과 계산 불가
        if (validStages.isEmpty()) {
            return null
        }

        val totalFrames =
            validStages.sumOf {
                it.totalFrames
            }

        if (totalFrames == 0) {
            return null
        }

        val bodyNormalFrames =
            validStages.sumOf {
                it.bodyNormalFrames
            }

        val bowArmNormalFrames =
            validStages.sumOf {
                it.bowArmNormalFrames
            }

        val drawArmNormalFrames =
            validStages.sumOf {
                it.drawArmNormalFrames
            }

        val bodyScore =
            calculatePercentage(
                bodyNormalFrames,
                totalFrames
            )

        val bowArmScore =
            calculatePercentage(
                bowArmNormalFrames,
                totalFrames
            )

        val drawArmScore =
            calculatePercentage(
                drawArmNormalFrames,
                totalFrames
            )

        val overallScore =
            (
                    bodyScore +
                            bowArmScore +
                            drawArmScore
                    ) / 3

        val scoreCalculator =
            PostureScoreCalculator()

        return FinalPostureResult(
            overallScore = overallScore,
            bodyScore = bodyScore,
            bowArmScore = bowArmScore,
            drawArmScore = drawArmScore,

            stage1Score =
                scoreCalculator
                    .calculate(stage1)
                    ?.overallScore,

            stage2Score =
                scoreCalculator
                    .calculate(stage2)
                    ?.overallScore,

            stage3Score =
                scoreCalculator
                    .calculate(stage3)
                    ?.overallScore
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