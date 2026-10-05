package com.beodysseus.poseprototype.feedback

class PostureWarningTracker(
    private val warningDurationMs: Long = 2000L,
    private val cooldownMs: Long = 5000L
) {

    private var warningStartTime: Long? = null
    private var lastTriggeredTime: Long? = null

    fun update(
        status: PostureStatus?,
        currentTimeMs: Long
    ): Boolean {

        // 정상 또는 측정 불가 상태가 되면
        // WARNING 지속시간을 다시 처음부터 측정
        if (status != PostureStatus.WARNING) {
            warningStartTime = null
            return false
        }

        // WARNING이 처음 발생한 시점 저장
        if (warningStartTime == null) {
            warningStartTime = currentTimeMs
            return false
        }

        // 아직 2초 이상 지속되지 않았다면 안내하지 않음
        if (
            currentTimeMs - warningStartTime!! <
            warningDurationMs
        ) {
            return false
        }

        // 이전 TTS 이후 5초가 지나지 않았다면
        // 반복 안내하지 않음
        if (
            lastTriggeredTime != null &&
            currentTimeMs - lastTriggeredTime!! <
            cooldownMs
        ) {
            return false
        }

        // TTS 실행 시점 기록
        lastTriggeredTime = currentTimeMs

        return true
    }

    fun reset() {
        warningStartTime = null
        lastTriggeredTime = null
    }
}