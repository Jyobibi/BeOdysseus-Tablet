package com.beodysseus.poseprototype

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_result
        )

        val overallScore =
            intent.getIntExtra("overallScore", 0)

        val bodyScore =
            intent.getIntExtra("bodyScore", 0)

        val bowArmScore =
            intent.getIntExtra("bowArmScore", 0)

        val drawArmScore =
            intent.getIntExtra("drawArmScore", 0)

        val stage1Score =
            intent.getIntExtra("stage1Score", -1)

        val stage2Score =
            intent.getIntExtra("stage2Score", -1)

        val stage3Score =
            intent.getIntExtra("stage3Score", -1)

        // 종합 점수
        findViewById<TextView>(
            R.id.overallScoreText
        ).text = "${overallScore}점"

        // 별점
        val stars =
            when {
                overallScore >= 90 -> "★★★★★"
                overallScore >= 80 -> "★★★★☆"
                overallScore >= 70 -> "★★★☆☆"
                overallScore >= 60 -> "★★☆☆☆"
                else -> "★☆☆☆☆"
            }

        findViewById<TextView>(
            R.id.starScoreText
        ).text = stars

        // 항목별 평가
        findViewById<TextView>(
            R.id.bodyScoreText
        ).text =
            "• 상체 자세                 ${bodyScore}점"

        findViewById<TextView>(
            R.id.bowArmScoreText
        ).text =
            "• 활팔 펴짐                 ${bowArmScore}점"

        findViewById<TextView>(
            R.id.drawArmScoreText
        ).text =
            "• 양팔 정렬                 ${drawArmScore}점"

        // Stage별 점수
        findViewById<TextView>(
            R.id.stage1ScoreText
        ).text =
            "STAGE 1\n${scoreText(stage1Score)}"

        findViewById<TextView>(
            R.id.stage2ScoreText
        ).text =
            "STAGE 2\n${scoreText(stage2Score)}"

        findViewById<TextView>(
            R.id.stage3ScoreText
        ).text =
            "STAGE 3\n${scoreText(stage3Score)}"

        // 결과 코멘트
        val comment =
            when {
                stage1Score >= 0 &&
                        stage2Score >= 0 &&
                        stage3Score >= 0 &&
                        stage3Score > stage1Score -> {
                    "후반으로 갈수록 자세가 안정되었습니다."
                }

                overallScore >= 85 -> {
                    "전반적으로 안정적인 자세를 유지했습니다."
                }

                overallScore >= 70 -> {
                    "대체로 안정적인 자세를 유지했습니다."
                }

                else -> {
                    "조금 더 안정적인 자세를 유지해보세요."
                }
            }

        findViewById<TextView>(
            R.id.resultCommentText
        ).text = comment
    }

    private fun scoreText(
        score: Int
    ): String {

        return if (score >= 0) {
            "${score}점"
        } else {
            "--점"
        }
    }
}