package com.beodysseus.poseprototype.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpSender {

    companion object {
        private const val TAG = "UdpSender"
    }

    fun send(
        message: String,
        targetIp: String,
        targetPort: Int = NetworkConstants.PHONE_PORT
    ) {
        Thread {
            try {
                val data = message.toByteArray(Charsets.UTF_8)
                val address = InetAddress.getByName(targetIp)

                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        address,
                        targetPort
                    )

                    socket.send(packet)
                }

                Log.d(
                    TAG,
                    "UDP SENT → $targetIp:$targetPort : $message"
                )

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "UDP send error",
                    e
                )
            }
        }.start()
    }
}