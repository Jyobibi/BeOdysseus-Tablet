package com.beodysseus.poseprototype.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class PostureTtsManager(
    context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "PostureTtsManager"
    }

    private val textToSpeech =
        TextToSpeech(
            context.applicationContext,
            this
        )

    private var isReady = false

    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS initialization failed")
            return
        }

        val result =
            textToSpeech.setLanguage(
                Locale.KOREAN
            )

        isReady =
            result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

        if (isReady) {
            Log.d(TAG, "TTS READY")
        } else {
            Log.e(TAG, "Korean TTS is not supported")
        }
    }

    fun speak(
        message: String
    ) {

        if (!isReady) {
            return
        }

        textToSpeech.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "POSTURE_FEEDBACK"
        )
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}