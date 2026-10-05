package com.beodysseus.poseprototype

data class PoseKeyPoint(
    val x: Float,
    val y: Float,
    val confidence: Float
)

data class PoseBoundingBox(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

data class RawPersonDetection(
    val boundingBox: PoseBoundingBox,
    val keypoints: List<PoseKeyPoint>,
    val personConfidence: Float
)

enum class TrackingState {
    SEARCHING,
    REGISTERING,
    LOCKED,
    RECOVERING,
    LOST
}

enum class MeasurementPhase {
    WAITING_FRONT,
    TURNING,
    SIDE_MEASURING
}

enum class ArmSide {
    LEFT,
    RIGHT
}

data class TrackerUpdate(
    val state: TrackingState,
    val remainingSeconds: Int = 0,
    val detection: RawPersonDetection? = null
)

/*
 * 상아 씨에게 넘길 최종 자세 측정값.
 *
 * 여기에는 GOOD/WARNING 같은 판정은 넣지 않는다.
 * 순수하게 수빈 파트에서 계산한 측정값만 전달한다.
 */
data class PoseMetricOutput(
    val bodyLeanDegree: Float?,
    val bowArmStraightnessErrorDegree: Float?,
    val drawArmAlignmentErrorDegree: Float?
)

data class PoseMetricsResult(

    // Debug / 기존 계산 확인용
    val shoulderTiltDegree: Float? = null,
    val armAlignmentDegree: Float? = null,

    // 최종 사용 지표
    val bodyLeanDegree: Float? = null,
    val bowArmStraightnessErrorDegree: Float? = null,
    val drawArmAlignmentErrorDegree: Float? = null,

    // Debug / 확인용
    val bowArmSide: ArmSide? = null,
    val drawArmSide: ArmSide? = null,
    val leftElbowAngleDegree: Float? = null,
    val rightElbowAngleDegree: Float? = null
)

data class TrackedPose(
    val boundingBox: PoseBoundingBox,
    val keypoints: List<PoseKeyPoint>,
    val personConfidence: Float,

    // Debug
    val shoulderTiltDegree: Float?,
    val armAlignmentDegree: Float?,

    // 최종 사용 지표
    val bodyLeanDegree: Float?,
    val bowArmStraightnessErrorDegree: Float?,
    val drawArmAlignmentErrorDegree: Float?,

    // Debug
    val bowArmSide: ArmSide?,
    val drawArmSide: ArmSide?,
    val leftElbowAngleDegree: Float?,
    val rightElbowAngleDegree: Float?
) {

    /*
     * 상아 씨 쪽에서 필요한 세 값만
     * 깔끔하게 꺼낼 때 사용.
     */
    fun toMetricOutput(): PoseMetricOutput {

        return PoseMetricOutput(
            bodyLeanDegree =
                bodyLeanDegree,

            bowArmStraightnessErrorDegree =
                bowArmStraightnessErrorDegree,

            drawArmAlignmentErrorDegree =
                drawArmAlignmentErrorDegree
        )
    }
}

data class PoseFrameResult(
    val state: TrackingState,

    val remainingSeconds: Int,

    val measurementPhase: MeasurementPhase,

    val measurementRemainingSeconds: Int,

    val pose: TrackedPose?,

    val sourceWidth: Int,
    val sourceHeight: Int
)