package com.beodysseus.poseprototype.network

class PairingManager {

    private var pairedCode: String? = null

    val isPaired: Boolean
        get() = pairedCode != null

    fun pair(
        receivedCode: String,
        enteredCode: String
    ): Boolean {

        if (!isValidCode(receivedCode) ||
            !isValidCode(enteredCode)
        ) {
            return false
        }

        if (receivedCode != enteredCode) {
            return false
        }

        pairedCode = receivedCode

        return true
    }

    fun reset() {
        pairedCode = null
    }

    private fun isValidCode(
        code: String
    ): Boolean {

        return code.length == 4 &&
                code.all { it.isDigit() }
    }
}