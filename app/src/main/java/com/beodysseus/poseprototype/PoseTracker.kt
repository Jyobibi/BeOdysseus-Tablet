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
    private val appearanceExtractor: AppearanceExtractor
) {

    companion object {

        private const val TAG =
            "BeOdysseusTracker"

        private const val AUTO_REGISTER_MS =
            5000L

        private const val REGISTER_LOST_RESET_MS =
            1000L

        // 정면 추적
        private const val TRACKING_MISS_LIMIT =
            3

        // 측면에서는 관절이 가려질 수 있으므로 조금 더 여유
        private const val SIDE_TRACKING_MISS_LIMIT =
            5

        private const val RECOVERY_TIMEOUT_MS =
            4000L

        private const val RECOVERY_REQUIRED_FRAMES =
            3

        private const val SIDE_RECOVERY_REQUIRED_FRAMES =
            2

        private const val KEYPOINT_CONFIDENCE =
            0.50f

        // 정면 등록
        private const val REGISTER_APPEARANCE_MIN =
            0.45f

        private const val REGISTER_MAX_SCORE =
            0.55f

        // 정면 LOCK
        private const val LOCKED_APPEARANCE_MIN =
            0.58f

        private const val LOCKED_MAX_SCORE =
            0.44f

        // 측면 LOCK
        private const val SIDE_LOCKED_APPEARANCE_MIN =
            0.42f

        private const val SIDE_LOCKED_MAX_SCORE =
            0.56f

        // 정면 복구
        private const val RECOVERY_APPEARANCE_MIN =
            0.68f

        private const val RECOVERY_MAX_SCORE =
            0.34f

        // 측면 복구
        private const val SIDE_RECOVERY_APPEARANCE_MIN =
            0.45f

        private const val SIDE_RECOVERY_MAX_SCORE =
            0.48f
    }

    private var state =
        TrackingState.SEARCHING

    // ============================================================
    // 최초 등록
    // ============================================================

    private var registrationStartTime =
        0L

    private var lastRegistrationSeenTime =
        0L

    private var registrationAnchor:
            RawPersonDetection? = null

    private var registrationAppearance:
            AppearanceDescriptor? = null

    // ============================================================
    // LOCK 사용자
    // ============================================================

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

    // ============================================================
    // Recovery
    // ============================================================

    private var recoveryStartTime =
        0L

    private var recoveryMatchCount =
        0

    private var recoveryCandidateAppearance:
            AppearanceDescriptor? = null

    // ============================================================
    // 정면 / 측면 Tracking Mode
    // ============================================================

    /*
     * false:
     * 정면 USER 등록/추적
     *
     * true:
     * 정면 → 측면 전환 및 측면 측정
     */
    private var sidePoseMode =
        false

    // ============================================================
    // 외부 호출
    // ============================================================

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

        sidePoseMode =
            false

        Log.d(
            TAG,
            "TRACKER RESET"
        )
    }

    /*
     * 정면 등록이 끝나고
     * 사용자가 측면으로 돌아가기 시작할 때 호출.
     *
     * 기존 USER 01은 유지하되
     * 추적 기준만 측면 전환에 맞게 조금 느슨하게 한다.
     */
    fun beginSideTransitionMode() {

        sidePoseMode =
            true

        trackingMissCount =
            0

        recoveryMatchCount =
            0

        recoveryCandidateAppearance =
            null

        Log.d(
            TAG,
            "SIDE TRANSITION MODE ENABLED"
        )
    }

    /*
     * 측면 자세가 완성된 뒤
     * 현재 측면 모습을 USER 01의 새로운 추적 기준으로 사용.
     */
    fun refreshSideProfile(
        detection: RawPersonDetection,
        bitmap: Bitmap
    ): Boolean {

        val appearance =
            appearanceExtractor.extract(
                bitmap,
                detection
            ) ?: return false

        sidePoseMode =
            true

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

        Log.d(
            TAG,
            "SIDE PROFILE REFRESHED"
        )

        return true
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

        val match =
            selectInitialCandidate(
                detections,
                bitmap
            )
                ?: return TrackerUpdate(
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
                box.centerX -
                        0.5f

            val dy =
                box.centerY -
                        0.5f

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
                        (
                                1f -
                                        detection.personConfidence
                                ) *
                        0.15f -
                        area *
                        0.25f

            if (
                best == null ||
                score <
                best.score
            ) {

                best =
                    CandidateMatch(
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
                        (
                                1f -
                                        appearanceSimilarity
                                ) *
                        0.35f

            if (
                score >
                REGISTER_MAX_SCORE
            ) {
                continue
            }

            if (
                best == null ||
                score <
                best.score
            ) {

                best =
                    CandidateMatch(
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
    // LOCK
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

            val missLimit =
                if (
                    sidePoseMode
                ) {
                    SIDE_TRACKING_MISS_LIMIT
                } else {
                    TRACKING_MISS_LIMIT
                }

            if (
                trackingMissCount >=
                missLimit
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

        /*
         * 정면 → 측면으로 회전하는 동안
         * 옷의 보이는 영역도 변하므로
         * Appearance를 천천히 현재 모습에 적응시킨다.
         */
        if (
            sidePoseMode
        ) {

            lockedAppearance =
                appearanceExtractor.blend(
                    lockedAppearance,
                    match.appearance,
                    0.10f
                )
        }

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

            val trackable =
                if (
                    sidePoseMode
                ) {

                    isSideTrackable(
                        detection
                    )

                } else {

                    isTrackable(
                        detection
                    )
                }

            if (
                !trackable
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

            val minimumAppearance =
                if (
                    sidePoseMode
                ) {
                    SIDE_LOCKED_APPEARANCE_MIN
                } else {
                    LOCKED_APPEARANCE_MIN
                }

            if (
                appearanceSimilarity <
                minimumAppearance
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

            if (
                sidePoseMode
            ) {

                if (
                    positionDistance >
                    0.45f &&
                    appearanceSimilarity <
                    0.65f
                ) {
                    continue
                }

            } else {

                if (
                    positionDistance >
                    0.38f &&
                    appearanceSimilarity <
                    0.75f
                ) {
                    continue
                }
            }

            val sizeDifference =
                boxSizeDifference(
                    currentBox,
                    box
                )

            val candidateRatio =
                bodyRatio(
                    detection
                )

            val ratioDifference =
                if (
                    trackedBodyRatio >
                    0f &&
                    candidateRatio >
                    0f
                ) {

                    relativeDifference(
                        trackedBodyRatio,
                        candidateRatio
                    )

                } else {

                    0.25f
                }

            val score =
                if (
                    sidePoseMode
                ) {

                    (
                            1f -
                                    appearanceSimilarity
                            ) *
                            0.45f +
                            positionDistance *
                            0.30f +
                            sizeDifference *
                            0.15f +
                            ratioDifference *
                            0.10f

                } else {

                    (
                            1f -
                                    appearanceSimilarity
                            ) *
                            0.58f +
                            positionDistance *
                            0.22f +
                            sizeDifference *
                            0.12f +
                            ratioDifference *
                            0.08f
                }

            val maxScore =
                if (
                    sidePoseMode
                ) {
                    SIDE_LOCKED_MAX_SCORE
                } else {
                    LOCKED_MAX_SCORE
                }

            if (
                score >
                maxScore
            ) {
                continue
            }

            if (
                best == null ||
                score <
                best.score
            ) {

                best =
                    CandidateMatch(
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
            ratio >
            0f
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

            val requiredSimilarity =
                if (
                    sidePoseMode
                ) {
                    0.60f
                } else {
                    0.78f
                }

            if (
                sameCandidateSimilarity >=
                requiredSimilarity
            ) {

                recoveryMatchCount++

            } else {

                recoveryMatchCount =
                    1
            }
        }

        recoveryCandidateAppearance =
            match.appearance

        val requiredFrames =
            if (
                sidePoseMode
            ) {
                SIDE_RECOVERY_REQUIRED_FRAMES
            } else {
                RECOVERY_REQUIRED_FRAMES
            }

        if (
            recoveryMatchCount >=
            requiredFrames
        ) {

            /*
             * 측면 상태에서는 현재 측면 Appearance를
             * 새로운 기준으로 사용.
             */
            val appearanceForLock =
                if (
                    sidePoseMode
                ) {

                    match.appearance

                } else {

                    lockedAppearance
                        ?: match.appearance
                }

            lockTarget(
                match.detection,
                appearanceForLock
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

            val valid =
                if (
                    sidePoseMode
                ) {

                    isSideTrackable(
                        detection
                    )

                } else {

                    isReliableForRegistration(
                        detection
                    )
                }

            if (
                !valid
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

            val appearanceMinimum =
                if (
                    sidePoseMode
                ) {
                    SIDE_RECOVERY_APPEARANCE_MIN
                } else {
                    RECOVERY_APPEARANCE_MIN
                }

            if (
                appearanceSimilarity <
                appearanceMinimum
            ) {
                continue
            }

            val candidateRatio =
                bodyRatio(
                    detection
                )

            val ratioDifference =
                if (
                    trackedBodyRatio >
                    0f &&
                    candidateRatio >
                    0f
                ) {

                    relativeDifference(
                        trackedBodyRatio,
                        candidateRatio
                    )

                } else {

                    0.25f
                }

            val positionDistance =
                trackedBox?.let {

                    boxCenterDistance(
                        it,
                        detection.boundingBox
                    )

                } ?: 0f

            val score =
                if (
                    sidePoseMode
                ) {

                    (
                            1f -
                                    appearanceSimilarity
                            ) *
                            0.55f +
                            ratioDifference *
                            0.15f +
                            positionDistance *
                            0.20f +
                            (
                                    1f -
                                            detection.personConfidence
                                    ) *
                            0.10f

                } else {

                    (
                            1f -
                                    appearanceSimilarity
                            ) *
                            0.75f +
                            ratioDifference *
                            0.15f +
                            (
                                    1f -
                                            detection.personConfidence
                                    ) *
                            0.10f
                }

            val maximumScore =
                if (
                    sidePoseMode
                ) {
                    SIDE_RECOVERY_MAX_SCORE
                } else {
                    RECOVERY_MAX_SCORE
                }

            if (
                score >
                maximumScore
            ) {
                continue
            }

            if (
                best == null ||
                score <
                best.score
            ) {

                best =
                    CandidateMatch(
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
    // Pose Validity
    // ============================================================

    /*
     * 정면 등록 기준.
     *
     * 양쪽 어깨 + 양쪽 골반 필요.
     */
    private fun isReliableForRegistration(
        detection: RawPersonDetection
    ): Boolean {

        if (
            detection.keypoints.size <
            13
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

        val points =
            listOf(
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

            if (
                point.x <
                0.03f ||
                point.x >
                0.97f ||
                point.y <
                0.03f ||
                point.y >
                0.97f
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
            shoulderSpan <
            0.04f ||
            torso <
            0.07f
        ) {
            return false
        }

        return true
    }

    /*
     * 일반 LOCK 추적.
     */
    private fun isTrackable(
        detection: RawPersonDetection
    ): Boolean {

        if (
            detection.keypoints.size <
            11
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

    /*
     * 측면에서는 반대쪽 관절이
     * 몸에 가려지는 것이 정상.
     *
     * 따라서 양쪽 관절을 모두 요구하지 않는다.
     */
    private fun isSideTrackable(
        detection: RawPersonDetection
    ): Boolean {

        if (
            detection.keypoints.size <
            13
        ) {
            return false
        }

        if (
            detection.personConfidence <
            0.35f
        ) {
            return false
        }

        val leftShoulder =
            detection.keypoints[5]

        val rightShoulder =
            detection.keypoints[6]

        val leftElbow =
            detection.keypoints[7]

        val rightElbow =
            detection.keypoints[8]

        val leftHip =
            detection.keypoints[11]

        val rightHip =
            detection.keypoints[12]

        val shoulderVisible =
            max(
                leftShoulder.confidence,
                rightShoulder.confidence
            ) >= 0.35f

        val elbowVisible =
            max(
                leftElbow.confidence,
                rightElbow.confidence
            ) >= 0.30f

        val hipVisible =
            max(
                leftHip.confidence,
                rightHip.confidence
            ) >= 0.30f

        return (
                shoulderVisible &&
                        elbowVisible &&
                        hipVisible
                )
    }

    // ============================================================
    // Body Geometry
    // ============================================================

    private fun bodyRatio(
        detection: RawPersonDetection
    ): Float {

        if (
            detection.keypoints.size <
            13
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
            shoulderSpan <=
            0f ||
            torso <=
            0f
        ) {
            return 0f
        }

        return shoulderSpan /
                torso
    }

    private fun torsoLength(
        detection: RawPersonDetection
    ): Float {

        if (
            detection.keypoints.size <
            13
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

        /*
         * 정면 기준 body ratio 계산용.
         * 측면에서 안 잡히면 0 반환하고,
         * 측면 Tracker에서는 해당 점수 비중을 낮게 사용.
         */
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
                    ) /
                    2f

        val shoulderCenterY =
            (
                    leftShoulder.y +
                            rightShoulder.y
                    ) /
                    2f

        val hipCenterX =
            (
                    leftHip.x +
                            rightHip.x
                    ) /
                    2f

        val hipCenterY =
            (
                    leftHip.y +
                            rightHip.y
                    ) /
                    2f

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
                ) /
                2f
    }

    private fun relativeDifference(
        reference: Float,
        current: Float
    ): Float {

        if (
            reference <=
            0f
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
            x2 -
                    x1

        val dy =
            y2 -
                    y1

        return sqrt(
            dx * dx +
                    dy * dy
        )
    }
}