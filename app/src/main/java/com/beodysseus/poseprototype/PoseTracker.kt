package com.beodysseus.poseprototype

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

private data class CandidateMatch(
    val detection: RawPersonDetection,
    val appearance: AppearanceDescriptor,
    val score: Float
)

class PoseTracker(
    private val appearanceExtractor:
    AppearanceExtractor
) {

    companion object {
        private const val TAG =
            "BeOdysseusTracker"

        private const val AUTO_REGISTER_MS =
            5000L

        private const val REGISTER_LOST_RESET_MS =
            1000L

        private const val TRACKING_MISS_LIMIT =
            3

        private const val RECOVERY_TIMEOUT_MS =
            4000L

        private const val RECOVERY_REQUIRED_FRAMES =
            3

        private const val KEYPOINT_CONFIDENCE =
            0.50f

        private const val REGISTER_APPEARANCE_MIN =
            0.45f

        private const val LOCKED_APPEARANCE_MIN =
            0.58f

        private const val RECOVERY_APPEARANCE_MIN =
            0.68f

        private const val REGISTER_MAX_SCORE =
            0.55f

        private const val LOCKED_MAX_SCORE =
            0.44f

        private const val RECOVERY_MAX_SCORE =
            0.34f
    }

    private var state =
        TrackingState.SEARCHING

    // 최초 등록
    private var registrationStartTime =
        0L

    private var lastRegistrationSeenTime =
        0L

    private var registrationAnchor:
            RawPersonDetection? = null

    private var registrationAppearance:
            AppearanceDescriptor? = null

    // LOCK 상태
    private var lockedAppearance:
            AppearanceDescriptor? = null

    private var trackedBox:
            PoseBoundingBox? = null

    private var trackedBodyRatio =
        0f

    private var velocityX =
        0f

    private var velocityY =
        0f

    private var trackingMissCount =
        0

    // RECOVERING
    private var recoveryStartTime =
        0L

    private var recoveryMatchCount =
        0

    private var recoveryCandidateAppearance:
            AppearanceDescriptor? = null

    fun restart() {

        state =
            TrackingState.SEARCHING

        registrationStartTime =
            0L

        lastRegistrationSeenTime =
            0L

        registrationAnchor =
            null

        registrationAppearance =
            null

        lockedAppearance =
            null

        trackedBox =
            null

        trackedBodyRatio =
            0f

        velocityX =
            0f

        velocityY =
            0f

        trackingMissCount =
            0

        recoveryStartTime =
            0L

        recoveryMatchCount =
            0

        recoveryCandidateAppearance =
            null

        Log.d(
            TAG,
            "TRACKER RESET"
        )
    }

    fun update(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): TrackerUpdate {

        return when (
            state
        ) {

            TrackingState.SEARCHING ->
                processSearching(
                    detections,
                    bitmap
                )

            TrackingState.REGISTERING ->
                processRegistering(
                    detections,
                    bitmap
                )

            TrackingState.LOCKED ->
                processLocked(
                    detections,
                    bitmap
                )

            TrackingState.RECOVERING ->
                processRecovering(
                    detections,
                    bitmap
                )

            TrackingState.LOST ->
                TrackerUpdate(
                    state =
                        TrackingState.LOST
                )
        }
    }

    // ============================================================
    // SEARCHING
    // ============================================================

    private fun processSearching(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): TrackerUpdate {

        val match = selectInitialCandidate(
            detections,
            bitmap
        ) ?: return TrackerUpdate(
            state =
                TrackingState.SEARCHING
        )

        val now =
            SystemClock.elapsedRealtime()

        state =
            TrackingState.REGISTERING

        registrationStartTime =
            now

        lastRegistrationSeenTime =
            now

        registrationAnchor =
            match.detection

        registrationAppearance =
            match.appearance

        Log.d(
            TAG,
            "REGISTERING START"
        )

        return TrackerUpdate(
            state =
                TrackingState.REGISTERING,

            remainingSeconds =
                5,

            detection =
                match.detection
        )
    }

    // ============================================================
    // REGISTERING
    // ============================================================

    private fun processRegistering(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): TrackerUpdate {

        val now =
            SystemClock.elapsedRealtime()

        val match =
            selectRegistrationMatch(
                detections,
                bitmap
            )

        if (
            match == null
        ) {

            if (
                now -
                lastRegistrationSeenTime >
                REGISTER_LOST_RESET_MS
            ) {

                resetRegistration()

                return TrackerUpdate(
                    state =
                        TrackingState.SEARCHING
                )
            }

            return TrackerUpdate(
                state =
                    TrackingState.REGISTERING,

                remainingSeconds =
                    remainingRegistrationSeconds(
                        now
                    )
            )
        }

        lastRegistrationSeenTime =
            now

        registrationAnchor =
            match.detection

        registrationAppearance =
            appearanceExtractor.blend(
                registrationAppearance,
                match.appearance,
                0.15f
            )

        if (
            now -
            registrationStartTime >=
            AUTO_REGISTER_MS
        ) {

            lockTarget(
                match.detection,
                registrationAppearance
                    ?: match.appearance
            )

            Log.d(
                TAG,
                "USER 01 LOCKED"
            )

            return TrackerUpdate(
                state =
                    TrackingState.LOCKED,

                detection =
                    match.detection
            )
        }

        return TrackerUpdate(
            state =
                TrackingState.REGISTERING,

            remainingSeconds =
                remainingRegistrationSeconds(
                    now
                ),

            detection =
                match.detection
        )
    }

    private fun selectInitialCandidate(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): CandidateMatch? {

        var best:
                CandidateMatch? = null

        for (
        detection in detections
        ) {

            if (
                !isReliableForRegistration(
                    detection
                )
            ) {
                continue
            }

            val appearance =
                appearanceExtractor.extract(
                    bitmap,
                    detection
                ) ?: continue

            val box =
                detection.boundingBox

            val dx =
                box.centerX - 0.5f

            val dy =
                box.centerY - 0.5f

            val centerDistance =
                sqrt(
                    dx * dx +
                            dy * dy
                )

            val area =
                (
                        box.width *
                                box.height
                        ).coerceIn(
                        0f,
                        1f
                    )

            val score =
                centerDistance *
                        0.60f +
                        (1f -
                                detection.personConfidence) *
                        0.15f -
                        area *
                        0.25f

            if (
                best == null ||
                score < best.score
            ) {

                best = CandidateMatch(
                    detection =
                        detection,

                    appearance =
                        appearance,

                    score =
                        score
                )
            }
        }

        return best
    }

    private fun selectRegistrationMatch(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): CandidateMatch? {

        val anchor =
            registrationAnchor
                ?: return null

        var best:
                CandidateMatch? = null

        for (
        detection in detections
        ) {

            if (
                !isReliableForRegistration(
                    detection
                )
            ) {
                continue
            }

            val appearance =
                appearanceExtractor.extract(
                    bitmap,
                    detection
                ) ?: continue

            val appearanceSimilarity =
                registrationAppearance?.let {
                    appearanceExtractor.similarity(
                        it,
                        appearance
                    )
                } ?: 1f

            if (
                appearanceSimilarity <
                REGISTER_APPEARANCE_MIN
            ) {
                continue
            }

            val positionDistance =
                boxCenterDistance(
                    anchor.boundingBox,
                    detection.boundingBox
                )

            val sizeDifference =
                boxSizeDifference(
                    anchor.boundingBox,
                    detection.boundingBox
                )

            val score =
                positionDistance *
                        0.45f +
                        sizeDifference *
                        0.20f +
                        (1f -
                                appearanceSimilarity) *
                        0.35f

            if (
                score >
                REGISTER_MAX_SCORE
            ) {
                continue
            }

            if (
                best == null ||
                score < best.score
            ) {

                best = CandidateMatch(
                    detection =
                        detection,

                    appearance =
                        appearance,

                    score =
                        score
                )
            }
        }

        return best
    }

    private fun resetRegistration() {

        state =
            TrackingState.SEARCHING

        registrationStartTime =
            0L

        lastRegistrationSeenTime =
            0L

        registrationAnchor =
            null

        registrationAppearance =
            null

        Log.d(
            TAG,
            "REGISTERING RESET"
        )
    }

    private fun remainingRegistrationSeconds(
        now: Long
    ): Int {

        val remaining =
            AUTO_REGISTER_MS -
                    (
                            now -
                                    registrationStartTime
                            )

        if (
            remaining <= 0
        ) {
            return 0
        }

        return ceil(
            remaining /
                    1000.0
        ).toInt()
    }

    // ============================================================
    // LOCKED
    // ============================================================

    private fun lockTarget(
        detection: RawPersonDetection,
        appearance: AppearanceDescriptor
    ) {

        state =
            TrackingState.LOCKED

        lockedAppearance =
            appearance

        trackedBox =
            detection.boundingBox

        trackedBodyRatio =
            bodyRatio(
                detection
            )

        velocityX =
            0f

        velocityY =
            0f

        trackingMissCount =
            0

        recoveryStartTime =
            0L

        recoveryMatchCount =
            0

        recoveryCandidateAppearance =
            null
    }

    private fun processLocked(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): TrackerUpdate {

        val match =
            selectLockedMatch(
                detections,
                bitmap
            )

        if (
            match == null
        ) {

            trackingMissCount++

            if (
                trackingMissCount >=
                TRACKING_MISS_LIMIT
            ) {

                state =
                    TrackingState.RECOVERING

                recoveryStartTime =
                    SystemClock.elapsedRealtime()

                recoveryMatchCount =
                    0

                recoveryCandidateAppearance =
                    null

                Log.d(
                    TAG,
                    "USER 01 -> RECOVERING"
                )

                return TrackerUpdate(
                    state =
                        TrackingState.RECOVERING
                )
            }

            return TrackerUpdate(
                state =
                    TrackingState.LOCKED
            )
        }

        trackingMissCount =
            0

        updateTrackedMotion(
            match.detection
        )

        return TrackerUpdate(
            state =
                TrackingState.LOCKED,

            detection =
                match.detection
        )
    }

    private fun selectLockedMatch(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): CandidateMatch? {

        val currentBox =
            trackedBox
                ?: return null

        val targetAppearance =
            lockedAppearance
                ?: return null

        val predictedX =
            currentBox.centerX +
                    velocityX

        val predictedY =
            currentBox.centerY +
                    velocityY

        var best:
                CandidateMatch? = null

        for (
        detection in detections
        ) {

            if (
                !isTrackable(
                    detection
                )
            ) {
                continue
            }

            val appearance =
                appearanceExtractor.extract(
                    bitmap,
                    detection
                ) ?: continue

            val appearanceSimilarity =
                appearanceExtractor.similarity(
                    targetAppearance,
                    appearance
                )

            if (
                appearanceSimilarity <
                LOCKED_APPEARANCE_MIN
            ) {
                continue
            }

            val box =
                detection.boundingBox

            val dx =
                box.centerX -
                        predictedX

            val dy =
                box.centerY -
                        predictedY

            val positionDistance =
                sqrt(
                    dx * dx +
                            dy * dy
                )

            /*
             * 멀리 있는 다른 사람은
             * 외형 유사도가 매우 높지 않으면 거부
             */
            if (
                positionDistance > 0.38f &&
                appearanceSimilarity < 0.75f
            ) {
                continue
            }

            val sizeDifference =
                boxSizeDifference(
                    currentBox,
                    box
                )

            val currentRatio =
                trackedBodyRatio

            val candidateRatio =
                bodyRatio(
                    detection
                )

            val ratioDifference =
                if (
                    currentRatio > 0f &&
                    candidateRatio > 0f
                ) {

                    relativeDifference(
                        currentRatio,
                        candidateRatio
                    )

                } else {
                    0.25f
                }

            val score =
                (1f -
                        appearanceSimilarity) *
                        0.58f +
                        positionDistance *
                        0.22f +
                        sizeDifference *
                        0.12f +
                        ratioDifference *
                        0.08f

            if (
                score >
                LOCKED_MAX_SCORE
            ) {
                continue
            }

            if (
                best == null ||
                score < best.score
            ) {

                best = CandidateMatch(
                    detection =
                        detection,

                    appearance =
                        appearance,

                    score =
                        score
                )
            }
        }

        return best
    }

    private fun updateTrackedMotion(
        detection: RawPersonDetection
    ) {

        val oldBox =
            trackedBox
                ?: detection.boundingBox

        val newBox =
            detection.boundingBox

        val deltaX =
            newBox.centerX -
                    oldBox.centerX

        val deltaY =
            newBox.centerY -
                    oldBox.centerY

        velocityX =
            velocityX *
                    0.50f +
                    deltaX *
                    0.50f

        velocityY =
            velocityY *
                    0.50f +
                    deltaY *
                    0.50f

        trackedBox =
            newBox

        val ratio =
            bodyRatio(
                detection
            )

        if (
            ratio > 0f
        ) {

            trackedBodyRatio =
                trackedBodyRatio *
                        0.90f +
                        ratio *
                        0.10f
        }
    }

    // ============================================================
    // RECOVERING
    // ============================================================

    private fun processRecovering(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): TrackerUpdate {

        val now =
            SystemClock.elapsedRealtime()

        if (
            now -
            recoveryStartTime >=
            RECOVERY_TIMEOUT_MS
        ) {

            state =
                TrackingState.LOST

            recoveryMatchCount =
                0

            recoveryCandidateAppearance =
                null

            Log.d(
                TAG,
                "USER 01 LOST"
            )

            return TrackerUpdate(
                state =
                    TrackingState.LOST
            )
        }

        val match =
            selectRecoveryMatch(
                detections,
                bitmap
            )

        if (
            match == null
        ) {

            recoveryMatchCount =
                0

            recoveryCandidateAppearance =
                null

            return TrackerUpdate(
                state =
                    TrackingState.RECOVERING
            )
        }

        val previous =
            recoveryCandidateAppearance

        if (
            previous == null
        ) {

            recoveryMatchCount =
                1

        } else {

            val sameCandidateSimilarity =
                appearanceExtractor.similarity(
                    previous,
                    match.appearance
                )

            if (
                sameCandidateSimilarity >=
                0.78f
            ) {
                recoveryMatchCount++
            } else {
                recoveryMatchCount =
                    1
            }
        }

        recoveryCandidateAppearance =
            match.appearance

        if (
            recoveryMatchCount >=
            RECOVERY_REQUIRED_FRAMES
        ) {

            lockTarget(
                match.detection,
                lockedAppearance
                    ?: match.appearance
            )

            Log.d(
                TAG,
                "USER 01 RECOVERED"
            )

            return TrackerUpdate(
                state =
                    TrackingState.LOCKED,

                detection =
                    match.detection
            )
        }

        return TrackerUpdate(
            state =
                TrackingState.RECOVERING
        )
    }

    private fun selectRecoveryMatch(
        detections: List<RawPersonDetection>,
        bitmap: Bitmap
    ): CandidateMatch? {

        val targetAppearance =
            lockedAppearance
                ?: return null

        var best:
                CandidateMatch? = null

        for (
        detection in detections
        ) {

            if (
                !isReliableForRegistration(
                    detection
                )
            ) {
                continue
            }

            val appearance =
                appearanceExtractor.extract(
                    bitmap,
                    detection
                ) ?: continue

            val appearanceSimilarity =
                appearanceExtractor.similarity(
                    targetAppearance,
                    appearance
                )

            if (
                appearanceSimilarity <
                RECOVERY_APPEARANCE_MIN
            ) {
                continue
            }

            val candidateRatio =
                bodyRatio(
                    detection
                )

            val ratioDifference =
                if (
                    trackedBodyRatio > 0f &&
                    candidateRatio > 0f
                ) {

                    relativeDifference(
                        trackedBodyRatio,
                        candidateRatio
                    )

                } else {
                    0.25f
                }

            val score =
                (1f -
                        appearanceSimilarity) *
                        0.75f +
                        ratioDifference *
                        0.15f +
                        (1f -
                                detection.personConfidence) *
                        0.10f

            if (
                score >
                RECOVERY_MAX_SCORE
            ) {
                continue
            }

            if (
                best == null ||
                score < best.score
            ) {

                best = CandidateMatch(
                    detection =
                        detection,

                    appearance =
                        appearance,

                    score =
                        score
                )
            }
        }

        return best
    }

    // ============================================================
    // Pose validity
    // ============================================================

    private fun isReliableForRegistration(
        detection: RawPersonDetection
    ): Boolean {

        if (
            detection.keypoints.size < 13
        ) {
            return false
        }

        val leftShoulder =
            detection.keypoints[5]

        val rightShoulder =
            detection.keypoints[6]

        val leftHip =
            detection.keypoints[11]

        val rightHip =
            detection.keypoints[12]

        val points = listOf(
            leftShoulder,
            rightShoulder,
            leftHip,
            rightHip
        )

        for (
        point in points
        ) {

            if (
                point.confidence <
                KEYPOINT_CONFIDENCE
            ) {
                return false
            }

            /*
             * 화면 가장자리에 걸친 반쪽 사용자는
             * 신규 등록/복구에서 제외
             */
            if (
                point.x < 0.03f ||
                point.x > 0.97f ||
                point.y < 0.03f ||
                point.y > 0.97f
            ) {
                return false
            }
        }

        val shoulderSpan =
            distance(
                leftShoulder.x,
                leftShoulder.y,
                rightShoulder.x,
                rightShoulder.y
            )

        val torso =
            torsoLength(
                detection
            )

        if (
            shoulderSpan < 0.04f ||
            torso < 0.07f
        ) {
            return false
        }

        return true
    }

    private fun isTrackable(
        detection: RawPersonDetection
    ): Boolean {

        if (
            detection.keypoints.size < 11
        ) {
            return false
        }

        val leftShoulder =
            detection.keypoints[5]

        val rightShoulder =
            detection.keypoints[6]

        return (
                leftShoulder.confidence >=
                        0.40f &&
                        rightShoulder.confidence >=
                        0.40f
                )
    }

    // ============================================================
    // Body geometry
    // ============================================================

    private fun bodyRatio(
        detection: RawPersonDetection
    ): Float {

        if (
            detection.keypoints.size < 13
        ) {
            return 0f
        }

        val leftShoulder =
            detection.keypoints[5]

        val rightShoulder =
            detection.keypoints[6]

        val shoulderSpan =
            distance(
                leftShoulder.x,
                leftShoulder.y,
                rightShoulder.x,
                rightShoulder.y
            )

        val torso =
            torsoLength(
                detection
            )

        if (
            shoulderSpan <= 0f ||
            torso <= 0f
        ) {
            return 0f
        }

        return shoulderSpan / torso
    }

    private fun torsoLength(
        detection: RawPersonDetection
    ): Float {

        if (
            detection.keypoints.size < 13
        ) {
            return 0f
        }

        val leftShoulder =
            detection.keypoints[5]

        val rightShoulder =
            detection.keypoints[6]

        val leftHip =
            detection.keypoints[11]

        val rightHip =
            detection.keypoints[12]

        if (
            leftShoulder.confidence <
            KEYPOINT_CONFIDENCE ||
            rightShoulder.confidence <
            KEYPOINT_CONFIDENCE ||
            leftHip.confidence <
            KEYPOINT_CONFIDENCE ||
            rightHip.confidence <
            KEYPOINT_CONFIDENCE
        ) {
            return 0f
        }

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

        return distance(
            shoulderCenterX,
            shoulderCenterY,
            hipCenterX,
            hipCenterY
        )
    }

    // ============================================================
    // Utility
    // ============================================================

    private fun boxCenterDistance(
        first: PoseBoundingBox,
        second: PoseBoundingBox
    ): Float {

        return distance(
            first.centerX,
            first.centerY,
            second.centerX,
            second.centerY
        )
    }

    private fun boxSizeDifference(
        first: PoseBoundingBox,
        second: PoseBoundingBox
    ): Float {

        val widthDifference =
            relativeDifference(
                first.width,
                second.width
            )

        val heightDifference =
            relativeDifference(
                first.height,
                second.height
            )

        return (
                widthDifference +
                        heightDifference
                ) / 2f
    }

    private fun relativeDifference(
        reference: Float,
        current: Float
    ): Float {

        if (
            reference <= 0f
        ) {
            return 1f
        }

        return (
                abs(
                    reference -
                            current
                ) /
                        max(
                            reference,
                            0.001f
                        )
                ).coerceIn(
                0f,
                1f
            )
    }

    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {

        val dx =
            x2 - x1

        val dy =
            y2 - y1

        return sqrt(
            dx * dx +
                    dy * dy
        )
    }
}