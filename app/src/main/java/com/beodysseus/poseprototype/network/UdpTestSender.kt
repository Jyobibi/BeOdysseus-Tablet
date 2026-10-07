package com.beodysseus.poseprototype.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object UdpTestSender {

    fun sendPairOffer() {
        Thread {
            try {
                val message = """
                    {
                      "v": 1,
                      "type": "pair_offer",
                      "from": "phone",
                      "seq": 1,
                      "session": "A1B2C3D4",
                      "code": "4821",
                      "port": 47800,
                      "device": "TEST_PHONE"
                    }
                """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("127.0.0.1"),
                        NetworkConstants.TABLET_PORT
                    )

                    socket.send(packet)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendPairConfirm() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "pair_confirm",
                  "from": "phone",
                  "seq": 2,
                  "session": "A1B2C3D4"
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("127.0.0.1"),
                        NetworkConstants.TABLET_PORT
                    )

                    socket.send(packet)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendStageStart() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "stage_start",
                  "from": "phone",
                  "seq": 4,
                  "session": "A1B2C3D4",
                  "stage": 1
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("127.0.0.1"),
                        NetworkConstants.TABLET_PORT
                    )
                    socket.send(packet)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendShot() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "shot",
                  "from": "phone",
                  "seq": 5,
                  "session": "A1B2C3D4",
                  "stage": 1,
                  "shotIndex": 1,
                  "hit": true,
                  "killed": false,
                  "accuracy": 85.0,
                  "stability": 90.0
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    socket.send(
                        DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("127.0.0.1"),
                            NetworkConstants.TABLET_PORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendStageEnd() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "stage_end",
                  "from": "phone",
                  "seq": 6,
                  "session": "A1B2C3D4",
                  "stage": 1
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    socket.send(
                        DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("127.0.0.1"),
                            NetworkConstants.TABLET_PORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendGameStart() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "game_start",
                  "from": "phone",
                  "seq": 3,
                  "session": "A1B2C3D4",
                  "gameId": "TEST_GAME"
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("127.0.0.1"),
                        NetworkConstants.TABLET_PORT
                    )

                    socket.send(packet)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendGameEnd() {
        Thread {
            try {
                val message = """
                {
                  "v": 1,
                  "type": "game_end",
                  "from": "phone",
                  "seq": 7,
                  "session": "A1B2C3D4",
                  "completed": true
                }
            """.trimIndent()

                val data = message.toByteArray(Charsets.UTF_8)

                DatagramSocket().use { socket ->
                    socket.send(
                        DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("127.0.0.1"),
                            NetworkConstants.TABLET_PORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}