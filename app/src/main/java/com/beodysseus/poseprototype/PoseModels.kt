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

data class TrackerUpdate(
    val state: TrackingState,
    val remainingSeconds: Int = 0,
    val detection: RawPersonDetection? = null
)

data class PoseMetricsResult(
    val shoulderTiltDegree: Float? = null,
    val bodyLeanDegree: Float? = null,
    val armAlignmentDegree: Float? = null,
    val leftElbowAngleDegree: Float? = null,
    val rightElbowAngleDegree: Float? = null
)

data class TrackedPose(
    val boundingBox: PoseBoundingBox,
    val keypoints: List<PoseKeyPoint>,
    val personConfidence: Float,

    val shoulderTiltDegree: Float?,
    val bodyLeanDegree: Float?,
    val armAlignmentDegree: Float?,

    val leftElbowAngleDegree: Float?,
    val rightElbowAngleDegree: Float?
)

data class PoseFrameResult(
    val state: TrackingState,
    val remainingSeconds: Int,
    val pose: TrackedPose?,
    val sourceWidth: Int,
    val sourceHeight: Int
)