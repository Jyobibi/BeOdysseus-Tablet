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

data class PoseMetricsResult(

    // 기존 값 - 호환용
    val shoulderTiltDegree: Float? = null,
    val armAlignmentDegree: Float? = null,

    // 최종 측면 양궁 지표
    val bodyLeanDegree: Float? = null,
    val bowArmStraightnessErrorDegree: Float? = null,
    val drawArmElbowAngleDegree: Float? = null,

    // 어떤 팔로 판단했는지 확인용
    val bowArmSide: ArmSide? = null,
    val drawArmSide: ArmSide? = null,

    // Debug / 향후 활용
    val leftElbowAngleDegree: Float? = null,
    val rightElbowAngleDegree: Float? = null
)

data class TrackedPose(
    val boundingBox: PoseBoundingBox,
    val keypoints: List<PoseKeyPoint>,
    val personConfidence: Float,

    // 기존 값
    val shoulderTiltDegree: Float?,
    val armAlignmentDegree: Float?,

    // 최종 측면 양궁 지표
    val bodyLeanDegree: Float?,
    val bowArmStraightnessErrorDegree: Float?,
    val drawArmElbowAngleDegree: Float?,

    val bowArmSide: ArmSide?,
    val drawArmSide: ArmSide?,

    val leftElbowAngleDegree: Float?,
    val rightElbowAngleDegree: Float?
)

data class PoseFrameResult(
    val state: TrackingState,

    val remainingSeconds: Int,

    val measurementPhase: MeasurementPhase,

    val measurementRemainingSeconds: Int,

    val pose: TrackedPose?,

    val sourceWidth: Int,
    val sourceHeight: Int
)