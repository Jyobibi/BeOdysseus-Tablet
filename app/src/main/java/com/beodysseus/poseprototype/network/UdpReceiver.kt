package com.beodysseus.poseprototype.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket

class UdpReceiver(
    private val port: Int = NetworkConstants.TABLET_PORT,
    private val onMessageReceived: (String, String) -> Unit
) {

    companion object {
        private const val TAG = "UdpReceiver"
    }

    private var socket: DatagramSocket? = null
    private var receiverThread: Thread? = null

    @Volatile
    private var isRunning = false

    fun start() {

        if (isRunning) {
            return
        }

        isRunning = true

        receiverThread = Thread {

            try {
                socket = DatagramSocket(port)

                Log.d(
                    TAG,
                    "UDP LISTENING ON PORT $port"
                )

                val buffer = ByteArray(4096)

                while (isRunning) {

                    val packet = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    socket?.receive(packet)

                    val message = String(
                        packet.data,
                        packet.offset,
                        packet.length,
                        Charsets.UTF_8
                    )

                    val senderIp =
                        packet.address.hostAddress ?: continue

                    Log.d(
                        TAG,
                        "UDP RECEIVED ← $senderIp : $message"
                    )

                    onMessageReceived(
                        message,
                        senderIp
                    )
                }

            } catch (e: Exception) {

                if (isRunning) {
                    Log.e(
                        TAG,
                        "UDP receive error",
                        e
                    )
                }

            } finally {
                socket?.close()
                socket = null
            }
        }

        receiverThread?.start()
    }

    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
        receiverThread = null
    }
}