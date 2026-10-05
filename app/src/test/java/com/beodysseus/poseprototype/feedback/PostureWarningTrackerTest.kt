package com.beodysseus.poseprototype.feedback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureWarningTrackerTest {

    @Test
    fun cooldownTest() {
        val tracker = PostureWarningTracker(
            warningDurationMs = 2000L,
            cooldownMs = 5000L
        )

        // WARNING 시작
        assertFalse(
            tracker.update(
                PostureStatus.WARNING,
                0L
            )
        )

        // 2초 지속 → 첫 TTS 실행
        assertTrue(
            tracker.update(
                PostureStatus.WARNING,
                2000L
            )
        )

        // 3초 → cooldown 중
        assertFalse(
            tracker.update(
                PostureStatus.WARNING,
                3000L
            )
        )

        // 첫 TTS로부터 5초 경과 → 다시 TTS 가능
        assertTrue(
            tracker.update(
                PostureStatus.WARNING,
                7000L
            )
        )
    }

    @Test
    fun warningDurationTest() {
        val tracker = PostureWarningTracker(
            warningDurationMs = 2000L
        )

        // WARNING 시작
        assertFalse(
            tracker.update(
                PostureStatus.WARNING,
                0L
            )
        )

        // 1초 지속 → 아직 TTS X
        assertFalse(
            tracker.update(
                PostureStatus.WARNING,
                1000L
            )
        )

        // 2초 지속 → TTS 가능
        assertTrue(
            tracker.update(
                PostureStatus.WARNING,
                2000L
            )
        )
    }

    @Test
    fun normalResetsWarningTest() {
        val tracker = PostureWarningTracker(
            warningDurationMs = 2000L
        )

        tracker.update(
            PostureStatus.WARNING,
            0L
        )

        tracker.update(
            PostureStatus.WARNING,
            1000L
        )

        // 중간에 정상 자세가 되면 초기화
        assertFalse(
            tracker.update(
                PostureStatus.NORMAL,
                1500L
            )
        )

        // 다시 WARNING이 되어도 새로 2초를 세야 함
        assertFalse(
            tracker.update(
                PostureStatus.WARNING,
                2000L
            )
        )

        assertTrue(
            tracker.update(
                PostureStatus.WARNING,
                4000L
            )
        )
    }
}