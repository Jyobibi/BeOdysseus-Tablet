package com.beodysseus.poseprototype.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingManagerTest {

    @Test
    fun correctCodePairsSuccessfully() {

        val manager =
            PairingManager()

        assertTrue(
            manager.pair(
                receivedCode = "1234",
                enteredCode = "1234"
            )
        )

        assertTrue(
            manager.isPaired
        )
    }

    @Test
    fun wrongCodeFails() {

        val manager =
            PairingManager()

        assertFalse(
            manager.pair(
                receivedCode = "1234",
                enteredCode = "5678"
            )
        )

        assertFalse(
            manager.isPaired
        )
    }

    @Test
    fun invalidCodeFails() {

        val manager =
            PairingManager()

        assertFalse(
            manager.pair(
                receivedCode = "123",
                enteredCode = "123"
            )
        )

        assertFalse(
            manager.pair(
                receivedCode = "12AB",
                enteredCode = "12AB"
            )
        )
    }

    @Test
    fun resetTest() {

        val manager =
            PairingManager()

        manager.pair(
            receivedCode = "1234",
            enteredCode = "1234"
        )

        manager.reset()

        assertFalse(
            manager.isPaired
        )
    }
}