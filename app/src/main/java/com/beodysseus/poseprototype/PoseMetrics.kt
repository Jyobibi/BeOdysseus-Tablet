package com.beodysseus.poseprototype

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PoseMetrics {

    companion object {

        private const val KEYPOINT_CONFIDENCE_THRESHOLD =
            0.35f

        /*
         * 공학제 시연 기준
         *
         * true  = 오른손잡이
         *         왼팔: 활팔
         *         오른팔: 당김팔
         *
         * false = 왼손잡이
         *         오른팔: 활팔
         *         왼팔: 당김팔
         */
        private const val RIGHT_HANDED =
            true
    }

    fun calculate(
        detection: RawPersonDetection
    ): PoseMetricsResult {

        val leftElbowAngle =
            calculateElbowAngle(
                detection,
                shoulderIndex = 5,
                elbowIndex = 7,
                wristIndex = 9
            )

        val rightElbowAngle =
            calculateElbowAngle(
                detection,
                shoulderIndex = 6,
                elbowIndex = 8,
                wristIndex = 10
            )

        /*
         * 오른손잡이
         * 활팔  = LEFT
         * 당김팔 = RIGHT
         *
         * 왼손잡이는 반대.
         */
        val bowElbowAngle =
            if (RIGHT_HANDED) {
                leftElbowAngle
            } else {
                rightElbowAngle
            }

        /*
         * 활팔이 완전한 직선이면
         *
         * elbow angle = 180°
         * error       = 0°
         */
        val bowArmStraightnessError =
            bowElbowAngle?.let {
                abs(
                    180f - it
                )
            }

        /*
         * 당김팔은 elbow angle 자체가 아니라
         * Elbow → Wrist 선의 수평 정렬 오차를 사용.
         */
        val drawArmAlignmentError =
            if (RIGHT_HANDED) {

                calculateForearmHorizontalError(
                    detection,
                    elbowIndex = 8,
                    wristIndex = 10
                )

            } else {

                calculateForearmHorizontalError(
                    detection,
                    elbowIndex = 7,
                    wristIndex = 9
                )
            }

        return PoseMetricsResult(

            // 기존 Debug 값
            shoulderTiltDegree =
                calculateShoulderTilt(
                    detection
                ),

            /*
             * 기존 armAlignmentDegree를
             * 당김팔 정렬 오차 값으로 사용.
             */
            armAlignmentDegree =
                drawArmAlignmentError,

            bodyLeanDegree =
                calculateBodyLean(
                    detection
                ),

            bowArmStraightnessErrorDegree =
                bowArmStraightnessError,

            /*
             * 기존 필드명은 아직 유지하지만
             * 값은 이제 "당김팔 정렬 오차"이다.
             *
             * 최종 인터페이스 정리 때
             * drawArmAlignmentErrorDegree로
             * 이름을 한 번에 변경할 예정.
             */
            drawArmAlignmentErrorDegree =
                drawArmAlignmentError,

            bowArmSide =
                if (RIGHT_HANDED) {
                    ArmSide.LEFT
                } else {
                    ArmSide.RIGHT
                },

            drawArmSide =
                if (RIGHT_HANDED) {
                    ArmSide.RIGHT
                } else {
                    ArmSide.LEFT
                },

            leftElbowAngleDegree =
                leftElbowAngle,

            rightElbowAngleDegree =
                rightElbowAngle
        )
    }

    // ============================================================
    // 1. 상체 기울기 오차
    // ============================================================

    private fun calculateBodyLean(
        detection: RawPersonDetection
    ): Float? {

        val leftShoulder =
            point(
                detection,
                5
            )

        val rightShoulder =
            point(
                detection,
                6
            )

        val leftHip =
            point(
                detection,
                11
            )

        val rightHip =
            point(
                detection,
                12
            )

        /*
         * 측면에서는 카메라에 더 잘 보이는
         * Shoulder-Hip 한 쌍을 사용한다.
         */
        val leftScore =
            if (
                leftShoulder != null &&
                leftHip != null
            ) {

                min(
                    leftShoulder.confidence,
                    leftHip.confidence
                )

            } else {

                -1f
            }

        val rightScore =
            if (
                rightShoulder != null &&
                rightHip != null
            ) {

                min(
                    rightShoulder.confidence,
                    rightHip.confidence
                )

            } else {

                -1f
            }

        if (
            leftScore < 0f &&
            rightScore < 0f
        ) {
            return null
        }

        val shoulder: PoseKeyPoint
        val hip: PoseKeyPoint

        if (
            leftScore >= rightScore
        ) {

            shoulder =
                leftShoulder
                    ?: return null

            hip =
                leftHip
                    ?: return null

        } else {

            shoulder =
                rightShoulder
                    ?: return null

            hip =
                rightHip
                    ?: return null
        }

        val dx =
            shoulder.x -
                    hip.x

        val dy =
            hip.y -
                    shoulder.y

        if (
            abs(dy) <
            0.0001f
        ) {
            return null
        }

        /*
         * 수직선 기준 오차.
         *
         * 몸통이 수직이면 0°.
         */
        val radians =
            atan2(
                abs(dx),
                abs(dy)
            )

        return Math.toDegrees(
            radians.toDouble()
        ).toFloat()
    }

    // ============================================================
    // 2. 활팔 펴짐 오차
    // ============================================================

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

        /*
         * Elbow를 꼭짓점으로:
         *
         * Shoulder ← Elbow → Wrist
         */

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
            length1 <=
            0.0001f ||
            length2 <=
            0.0001f
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

        cosine =
            min(
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

    // ============================================================
    // 3. 당김팔 정렬 오차
    // ============================================================

    private fun calculateForearmHorizontalError(
        detection: RawPersonDetection,
        elbowIndex: Int,
        wristIndex: Int
    ): Float? {

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

        val dx =
            wrist.x -
                    elbow.x

        val dy =
            wrist.y -
                    elbow.y

        if (
            abs(dx) <
            0.0001f &&
            abs(dy) <
            0.0001f
        ) {
            return null
        }

        /*
         * 수평선을 0°로 둔다.
         *
         * --------  0°
         *
         *    /      약 30°
         *
         *    |      90°
         */
        var degree =
            abs(
                Math.toDegrees(
                    atan2(
                        dy.toDouble(),
                        dx.toDouble()
                    )
                ).toFloat()
            )

        /*
         * 진행 방향이 왼쪽이어도
         * 수평 오차는 동일해야 하므로
         * 0~90° 범위로 변환.
         */
        if (
            degree >
            90f
        ) {

            degree =
                180f -
                        degree
        }

        return abs(
            degree
        )
    }

    // ============================================================
    // Shoulder Tilt - Debug
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
    // Utility
    // ============================================================

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
            KEYPOINT_CONFIDENCE_THRESHOLD
        ) {
            return null
        }

        return point
    }
}