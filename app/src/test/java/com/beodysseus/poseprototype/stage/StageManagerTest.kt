package com.beodysseus.poseprototype.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageManagerTest {

    @Test
    fun stageStartAndEndTest() {
        val manager = StageManager()

        // 처음에는 Stage 비활성화
        assertEquals(0, manager.currentStage)
        assertFalse(manager.isStageActive)

        // Stage 1 시작
        manager.startStage(1)

        assertEquals(1, manager.currentStage)
        assertTrue(manager.isStageActive)

        // Stage 종료
        manager.endStage()

        assertEquals(1, manager.currentStage)
        assertFalse(manager.isStageActive)
    }

    @Test
    fun resetTest() {
        val manager = StageManager()

        manager.startStage(2)
        manager.reset()

        assertEquals(0, manager.currentStage)
        assertFalse(manager.isStageActive)
    }

    @Test
    fun invalidStageTest() {
        val manager = StageManager()

        manager.startStage(4)

        assertEquals(0, manager.currentStage)
        assertFalse(manager.isStageActive)
    }
}