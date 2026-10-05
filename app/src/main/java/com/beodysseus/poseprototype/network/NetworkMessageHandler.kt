package com.beodysseus.poseprototype.network

data class NetworkGameEvent(
    val type: String,
    val stage: Int? = null
)

class NetworkMessageHandler {

    fun parse(
        message: String
    ): NetworkGameEvent? {

        val trimmedMessage =
            message.trim()

        // 예: STAGE_START:1
        if (
            trimmedMessage.startsWith(
                "${NetworkConstants.STAGE_START}:"
            )
        ) {

            val stageNumber =
                trimmedMessage
                    .substringAfter(
                        "${NetworkConstants.STAGE_START}:"
                    )
                    .toIntOrNull()
                    ?: return null

            if (stageNumber !in 1..3) {
                return null
            }

            return NetworkGameEvent(
                type = NetworkConstants.STAGE_START,
                stage = stageNumber
            )
        }

        // Stage 종료
        if (
            trimmedMessage ==
            NetworkConstants.STAGE_END
        ) {

            return NetworkGameEvent(
                type = NetworkConstants.STAGE_END
            )
        }

        // 게임 시작
        if (
            trimmedMessage ==
            NetworkConstants.GAME_START
        ) {

            return NetworkGameEvent(
                type = NetworkConstants.GAME_START
            )
        }

        // 게임 종료
        if (
            trimmedMessage ==
            NetworkConstants.GAME_END
        ) {

            return NetworkGameEvent(
                type = NetworkConstants.GAME_END
            )
        }

        return null
    }
}