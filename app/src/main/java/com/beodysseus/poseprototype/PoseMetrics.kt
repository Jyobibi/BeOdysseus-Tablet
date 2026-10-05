package com.beodysseus.poseprototype

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PoseMetrics {

    companion object {
        private const val CONFIDENCE_THRESHOLD =
            0.45f
    }

    fun calculate(
        detection: RawPersonDetection
    ): PoseMetricsResult {

        return PoseMetricsResult(
            shoulderTiltDegree =
                calculateShoulderTilt(
                    detection
                ),

            bodyLeanDegree =
                calculateBodyLean(
                    detection
                ),

            armAlignmentDegree =
                calculateArmAlignment(
                    detection
                ),

            leftElbowAngleDegree =
                calculateElbowAngle(
                    detection,
                    shoulderIndex = 5,
                    elbowIndex = 7,
                    wristIndex = 9
                ),

            rightElbowAngleDegree =
                calculateElbowAngle(
                    detection,
                    shoulderIndex = 6,
                    elbowIndex = 8,
                    wristIndex = 10
                )
        )
    }

    // ============================================================
    // Shoulder Tilt
    // ============================================================

    private fun calculateShoulderTilt(
        detection: RawPersonDetection
    ): Float? {

        val left =
            point(
                detection,
                5
            ) ?: return null

        val right =
            point(
                detection,
                6
            ) ?: return null

        val dx =
            right.x -
                    left.x

        if (
            abs(dx) <
            0.0001f
        ) {
            return null
        }

        /*
         * 기존 실제 전면카메라 테스트 기준 유지
         *
         * + : 사용자 오른쪽 어깨가 높음
         * - : 사용자 왼쪽 어깨가 높음
         */
        val radians =
            atan2(
                left.y -
                        right.y,
                abs(dx)
            )

        return Math.toDegrees(
            radians.toDouble()
        ).toFloat()
    }

    // ============================================================
    // Body Lean
    // ============================================================

    private fun calculateBodyLean(
        detection: RawPersonDetection
    ): Float? {

        val leftShoulder =
            point(
                detection,
                5
            ) ?: return null

        val rightShoulder =
            point(
                detection,
                6
            ) ?: return null

        val leftHip =
            point(
                detection,
                11
            ) ?: return null

        val rightHip =
            point(
                detection,
                12
            ) ?: return null

        val shoulderCenterX =
            (
                    leftShoulder.x +
                            rightShoulder.x
                    ) / 2f

        val shoulderCenterY =
            (
                    leftShoulder.y +
                            rightShoulder.y
                    ) / 2f

        val hipCenterX =
            (
                    leftHip.x +
                            rightHip.x
                    ) / 2f

        val hipCenterY =
            (
                    leftHip.y +
                            rightHip.y
                    ) / 2f

        val horizontal =
            shoulderCenterX -
                    hipCenterX

        val vertical =
            hipCenterY -
                    shoulderCenterY

        if (
            abs(vertical) <
            0.0001f
        ) {
            return null
        }

        val radians =
            atan2(
                horizontal,
                abs(vertical)
            )

        return Math.toDegrees(
            radians.toDouble()
        ).toFloat()
    }

    // ============================================================
    // Arm Alignment
    // ============================================================

    private fun calculateArmAlignment(
        detection: RawPersonDetection
    ): Float? {

        val left =
            calculateElbowAngle(
                detection,
                shoulderIndex = 5,
                elbowIndex = 7,
                wristIndex = 9
            )

        val right =
            calculateElbowAngle(
                detection,
                shoulderIndex = 6,
                elbowIndex = 8,
                wristIndex = 10
            )

        return when {
            left != null &&
                    right != null ->
                max(
                    left,
                    right
                )

            left != null ->
                left

            right != null ->
                right

            else ->
                null
        }
    }

    private fun calculateElbowAngle(
        detection: RawPersonDetection,
        shoulderIndex: Int,
        elbowIndex: Int,
        wristIndex: Int
    ): Float? {

        val shoulder =
            point(
                detection,
                shoulderIndex
            ) ?: return null

        val elbow =
            point(
                detection,
                elbowIndex
            ) ?: return null

        val wrist =
            point(
                detection,
                wristIndex
            ) ?: return null

        val vector1X =
            shoulder.x -
                    elbow.x

        val vector1Y =
            shoulder.y -
                    elbow.y

        val vector2X =
            wrist.x -
                    elbow.x

        val vector2Y =
            wrist.y -
                    elbow.y

        val length1 =
            sqrt(
                vector1X *
                        vector1X +
                        vector1Y *
                        vector1Y
            )

        val length2 =
            sqrt(
                vector2X *
                        vector2X +
                        vector2Y *
                        vector2Y
            )

        if (
            length1 <= 0f ||
            length2 <= 0f
        ) {
            return null
        }

        var cosine =
            (
                    vector1X *
                            vector2X +
                            vector1Y *
                            vector2Y
                    ) /
                    (
                            length1 *
                                    length2
                            )

        cosine = min(
            1f,
            max(
                -1f,
                cosine
            )
        )

        return Math.toDegrees(
            acos(
                cosine.toDouble()
            )
        ).toFloat()
    }

    private fun point(
        detection: RawPersonDetection,
        index: Int
    ): PoseKeyPoint? {

        if (
            index !in
            detection.keypoints.indices
        ) {
            return null
        }

        val point =
            detection.keypoints[index]

        if (
            point.confidence <
            CONFIDENCE_THRESHOLD
        ) {
            return null
        }

        return point
    }
}