package com.beodysseus.poseprototype.stage

class StageManager {

    var currentStage: Int = 0
        private set

    var isStageActive: Boolean = false
        private set

    /**
     * Stage 시작
     * stageNumber는 1~3만 허용
     */
    fun startStage(
        stageNumber: Int
    ) {
        if (stageNumber !in 1..3) {
            return
        }

        currentStage = stageNumber
        isStageActive = true
    }

    /**
     * 현재 Stage 종료
     */
    fun endStage() {
        isStageActive = false
    }

    /**
     * 게임 전체 종료 또는 초기화
     */
    fun reset() {
        currentStage = 0
        isStageActive = false
    }
}