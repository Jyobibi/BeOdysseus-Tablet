package com.beodysseus.poseprototype.network

import org.json.JSONObject

data class NetworkGameEvent(
    val type: String,
    val version: Int,
    val from: String,
    val seq: Int,
    val session: String,

    val code: String? = null,
    val port: Int? = null,
    val device: String? = null,

    val gameId: String? = null,
    val stage: Int? = null,
    val monster: String? = null,

    val shotIndex: Int? = null,
    val hit: Boolean? = null,
    val killed: Boolean? = null,
    val accuracy: Double? = null,
    val stability: Double? = null,

    val cleared: Boolean? = null,
    val totalScore: Int? = null,
    val totalHits: Int? = null,
    val totalShots: Int? = null,
    val hitRate: Double? = null,
    val avgAccuracy: Double? = null,
    val avgStability: Double? = null,

    val completed: Boolean? = null
)

class NetworkMessageHandler {

    fun parse(
        message: String
    ): NetworkGameEvent? {

        return try {

            val json =
                JSONObject(message.trim())

            val version =
                json.optInt("v", -1)

            val type =
                json.optString("type", "")

            val from =
                json.optString("from", "")

            val seq =
                json.optInt("seq", -1)

            val session =
                json.optString("session", "")

            // 공통 필드 검증
            if (
                version != NetworkConstants.PROTOCOL_VERSION ||
                from != NetworkConstants.PHONE ||
                type.isBlank() ||
                seq < 1 ||
                session.length != 8
            ) {
                return null
            }

            NetworkGameEvent(
                type = type,
                version = version,
                from = from,
                seq = seq,
                session = session,

                code =
                    json.optString("code")
                        .takeIf { it.isNotBlank() },

                port =
                    if (json.has("port")) {
                        json.optInt("port")
                    } else {
                        null
                    },

                device =
                    json.optString("device")
                        .takeIf { it.isNotBlank() },

                gameId =
                    json.optString("gameId")
                        .takeIf { it.isNotBlank() },

                stage =
                    if (json.has("stage")) {
                        json.optInt("stage")
                    } else {
                        null
                    },

                monster =
                    json.optString("monster")
                        .takeIf { it.isNotBlank() },

                shotIndex =
                    if (json.has("shotIndex")) {
                        json.optInt("shotIndex")
                    } else {
                        null
                    },

                hit =
                    if (json.has("hit")) {
                        json.optBoolean("hit")
                    } else {
                        null
                    },

                killed =
                    if (json.has("killed")) {
                        json.optBoolean("killed")
                    } else {
                        null
                    },

                accuracy =
                    if (json.has("accuracy")) {
                        json.optDouble("accuracy")
                    } else {
                        null
                    },

                stability =
                    if (json.has("stability")) {
                        json.optDouble("stability")
                    } else {
                        null
                    },

                cleared =
                    if (json.has("cleared")) {
                        json.optBoolean("cleared")
                    } else {
                        null
                    },

                totalScore =
                    if (json.has("totalScore")) {
                        json.optInt("totalScore")
                    } else {
                        null
                    },

                totalHits =
                    if (json.has("totalHits")) {
                        json.optInt("totalHits")
                    } else {
                        null
                    },

                totalShots =
                    if (json.has("totalShots")) {
                        json.optInt("totalShots")
                    } else {
                        null
                    },

                hitRate =
                    if (json.has("hitRate")) {
                        json.optDouble("hitRate")
                    } else {
                        null
                    },

                avgAccuracy =
                    if (json.has("avgAccuracy")) {
                        json.optDouble("avgAccuracy")
                    } else {
                        null
                    },

                avgStability =
                    if (json.has("avgStability")) {
                        json.optDouble("avgStability")
                    } else {
                        null
                    },

                completed =
                    if (json.has("completed")) {
                        json.optBoolean("completed")
                    } else {
                        null
                    }
            )

        } catch (e: Exception) {
            null
        }
    }
}