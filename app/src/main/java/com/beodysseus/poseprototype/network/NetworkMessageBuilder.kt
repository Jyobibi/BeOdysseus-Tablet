package com.beodysseus.poseprototype.network

import org.json.JSONObject

object NetworkMessageBuilder {

    // 사용자가 4자리 코드를 입력했을 때 폰으로 전송
    fun createPairRequest(
        code: String,
        session: String,
        seq: Int
    ): String {

        return JSONObject().apply {
            put("v", NetworkConstants.PROTOCOL_VERSION)
            put("type", "pair_request")
            put("from", NetworkConstants.TABLET)
            put("seq", seq)
            put("session", session)
            put("code", code)
        }.toString()
    }

    // 게임 기록을 정상적으로 받았다는 ACK
    fun createAck(
        session: String,
        seq: Int,
        ackSeq: Int
    ): String {

        return JSONObject().apply {
            put("v", NetworkConstants.PROTOCOL_VERSION)
            put("type", "ack")
            put("from", NetworkConstants.TABLET)
            put("seq", seq)
            put("session", session)
            put("ackSeq", ackSeq)
        }.toString()
    }
}