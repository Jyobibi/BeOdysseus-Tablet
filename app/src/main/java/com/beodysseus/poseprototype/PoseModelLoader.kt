package com.beodysseus.poseprototype

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class PoseModelLoader(context: Context) {

    private val interpreter: Interpreter

    private val inputWidth = 640
    private val inputHeight = 640

    init {
        val modelBytes = context.assets
            .open("yolo26n-pose_w8a32.tflite")
            .use { it.readBytes() }

        val modelBuffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())

        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        val options = Interpreter.Options()
        options.setNumThreads(4)

        interpreter = Interpreter(modelBuffer, options)

        Log.d("BeOdysseusPose", "MODEL LOADED")

        Log.d(
            "BeOdysseusPose",
            "Input: ${interpreter.getInputTensor(0).shape().contentToString()}"
        )

        Log.d(
            "BeOdysseusPose",
            "Output: ${interpreter.getOutputTensor(0).shape().contentToString()}"
        )
    }

    fun runTestInference(bitmap: Bitmap) {

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            inputWidth,
            inputHeight,
            true
        )

        val pixels = IntArray(inputWidth * inputHeight)

        resizedBitmap.getPixels(
            pixels,
            0,
            inputWidth,
            0,
            0,
            inputWidth,
            inputHeight
        )

        val inputBuffer = ByteBuffer
            .allocateDirect(
                1 * 3 * inputWidth * inputHeight * 4
            )
            .order(ByteOrder.nativeOrder())

        // R 채널
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            inputBuffer.putFloat(r / 255.0f)
        }

        // G 채널
        for (pixel in pixels) {
            val g = (pixel shr 8) and 0xFF
            inputBuffer.putFloat(g / 255.0f)
        }

        // B 채널
        for (pixel in pixels) {
            val b = pixel and 0xFF
            inputBuffer.putFloat(b / 255.0f)
        }

        inputBuffer.rewind()

        // 모델 출력: [1, 56, 8400]
        val output =
            Array(1) {
                Array(56) {
                    FloatArray(8400)
                }
            }

        val startTime = System.currentTimeMillis()

        interpreter.run(
            inputBuffer,
            output
        )

        val inferenceTime =
            System.currentTimeMillis() - startTime

        Log.d(
            "BeOdysseusPose",
            "Inference time: ${inferenceTime} ms"
        )

        // =========================
        // 가장 confidence 높은 사람 찾기
        // =========================

        var bestIndex = -1
        var bestConfidence = 0f

        for (i in 0 until 8400) {

            val confidence =
                output[0][4][i]

            if (confidence > bestConfidence) {
                bestConfidence = confidence
                bestIndex = i
            }
        }

        if (
            bestIndex == -1 ||
            bestConfidence < 0.25f
        ) {

            Log.d(
                "BeOdysseusPose",
                "NO PERSON DETECTED"
            )

            resizedBitmap.recycle()
            return
        }

        // =========================
        // COCO Pose Keypoints
        // 5 = Left Shoulder
        // 6 = Right Shoulder
        // =========================

        val leftShoulderIndex = 5
        val rightShoulderIndex = 6

        val leftBase =
            5 + (leftShoulderIndex * 3)

        val rightBase =
            5 + (rightShoulderIndex * 3)

        val leftShoulderX =
            output[0][leftBase][bestIndex]

        val leftShoulderY =
            output[0][leftBase + 1][bestIndex]

        val leftShoulderConfidence =
            output[0][leftBase + 2][bestIndex]

        val rightShoulderX =
            output[0][rightBase][bestIndex]

        val rightShoulderY =
            output[0][rightBase + 1][bestIndex]

        val rightShoulderConfidence =
            output[0][rightBase + 2][bestIndex]

        Log.d(
            "BeOdysseusPose",
            "PERSON confidence=$bestConfidence"
        )

        Log.d(
            "BeOdysseusPose",
            "LEFT SHOULDER x=$leftShoulderX y=$leftShoulderY conf=$leftShoulderConfidence"
        )

        Log.d(
            "BeOdysseusPose",
            "RIGHT SHOULDER x=$rightShoulderX y=$rightShoulderY conf=$rightShoulderConfidence"
        )

        // =========================
        // Shoulder Tilt 계산
        // =========================

        val dx =
            rightShoulderX - leftShoulderX

        val dy =
            rightShoulderY - leftShoulderY

        val shoulderWidth =
            sqrt(
                dx * dx +
                        dy * dy
            )

        if (
            leftShoulderConfidence >= 0.5f &&
            rightShoulderConfidence >= 0.5f &&
            shoulderWidth > 0f
        ) {

            val shoulderTilt =
                (leftShoulderY - rightShoulderY) /
                        shoulderWidth

            Log.d(
                "BeOdysseusPose",
                "SHOULDER TILT = $shoulderTilt"
            )

        } else {

            Log.d(
                "BeOdysseusPose",
                "SHOULDER NOT RELIABLE"
            )
        }

        resizedBitmap.recycle()
    }

    fun close() {
        interpreter.close()
    }
}