package com.beodysseus.poseprototype.network

object NetworkConstants {

    // UDP 포트
    const val TABLET_PORT = 47801
    const val PHONE_PORT = 47800

    // Protocol
    const val PROTOCOL_VERSION = 1
    const val PHONE = "phone"
    const val TABLET = "tablet"

    // Pairing
    const val PAIR_OFFER = "pair_offer"
    const val PAIR_CONFIRM = "pair_confirm"
    const val PING = "ping"
    const val HELLO = "hello"
    const val UNPAIR = "unpair"
    const val PAIR_REQUEST = "pair_request"
    const val ACK = "ack"

    // Game
    const val GAME_START = "game_start"
    const val STAGE_START = "stage_start"
    const val SHOT = "shot"
    const val STAGE_END = "stage_end"
    const val GAME_END = "game_end"
}