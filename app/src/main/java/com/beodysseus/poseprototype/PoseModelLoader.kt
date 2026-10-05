package com.beodysseus.poseprototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class LetterboxResult(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val sourceWidth: Int,
    val sourceHeight: Int
)

class PoseModelLoader(
    context: Context
) {

    companion object {
        private const val TAG = "BeOdysseusPose"

        private const val INPUT_SIZE = 640
        private const val OUTPUT_COUNT = 8400
        private const val OUTPUT_CHANNELS = 56

        private const val PERSON_CONFIDENCE_THRESHOLD = 0.35f
        private const val NMS_IOU_THRESHOLD = 0.45f
        private const val MAX_PERSON_COUNT = 10
    }

    private val interpreter: Interpreter

    init {
        val modelBytes = context.assets
            .open("yolo26n-pose_w8a32.tflite")
            .use { it.readBytes() }

        val modelBuffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())

        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }

        interpreter = Interpreter(
            modelBuffer,
            options
        )

        Log.d(TAG, "YOLO26 POSE MODEL LOADED")
    }

    fun runInference(
        sourceBitmap: Bitmap
    ): List<RawPersonDetection> {

        val letterbox = createLetterbox(
            sourceBitmap
        )

        try {
            val inputBuffer = createInputBuffer(
                letterbox.bitmap
            )

            val output = Array(1) {
                Array(OUTPUT_CHANNELS) {
                    FloatArray(OUTPUT_COUNT)
                }
            }

            interpreter.run(
                inputBuffer,
                output
            )

            val detections = extractDetections(
                output,
                letterbox
            )

            return applyNms(
                detections
            )

        } finally {
            letterbox.bitmap.recycle()
        }
    }

    // ============================================================
    // Letterbox
    // ============================================================

    private fun createLetterbox(
        source: Bitmap
    ): LetterboxResult {

        val sourceWidth = source.width
        val sourceHeight = source.height

        val scale = min(
            INPUT_SIZE.toFloat() / sourceWidth,
            INPUT_SIZE.toFloat() / sourceHeight
        )

        val scaledWidth = (
                sourceWidth * scale
                ).roundToInt()

        val scaledHeight = (
                sourceHeight * scale
                ).roundToInt()

        val padX = (
                INPUT_SIZE - scaledWidth
                ) / 2f

        val padY = (
                INPUT_SIZE - scaledHeight
                ) / 2f

        val outputBitmap = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(
            outputBitmap
        )

        canvas.drawColor(
            Color.BLACK
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )

        val destination = RectF(
            padX,
            padY,
            padX + scaledWidth,
            padY + scaledHeight
        )

        canvas.drawBitmap(
            source,
            null,
            destination,
            paint
        )

        return LetterboxResult(
            bitmap = outputBitmap,
            scale = scale,
            padX = padX,
            padY = padY,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }

    // ============================================================
    // Model Input
    // ============================================================

    private fun createInputBuffer(
        bitmap: Bitmap
    ): ByteBuffer {

        val pixels = IntArray(
            INPUT_SIZE * INPUT_SIZE
        )

        bitmap.getPixels(
            pixels,
            0,
            INPUT_SIZE,
            0,
            0,
            INPUT_SIZE,
            INPUT_SIZE
        )

        val inputBuffer = ByteBuffer
            .allocateDirect(
                1 * 3 * INPUT_SIZE * INPUT_SIZE * 4
            )
            .order(
                ByteOrder.nativeOrder()
            )

        // R
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            inputBuffer.putFloat(
                r / 255f
            )
        }

        // G
        for (pixel in pixels) {
            val g = (pixel shr 8) and 0xFF
            inputBuffer.putFloat(
                g / 255f
            )
        }

        // B
        for (pixel in pixels) {
            val b = pixel and 0xFF
            inputBuffer.putFloat(
                b / 255f
            )
        }

        inputBuffer.rewind()

        return inputBuffer
    }

    // ============================================================
    // YOLO Output
    // ============================================================

    private fun extractDetections(
        output: Array<Array<FloatArray>>,
        letterbox: LetterboxResult
    ): List<RawPersonDetection> {

        val detections = mutableListOf<RawPersonDetection>()

        for (i in 0 until OUTPUT_COUNT) {

            val confidence = output[0][4][i]

            if (
                confidence <
                PERSON_CONFIDENCE_THRESHOLD
            ) {
                continue
            }

            val rawCenterX = modelCoordinateToPixel(
                output[0][0][i]
            )

            val rawCenterY = modelCoordinateToPixel(
                output[0][1][i]
            )

            val rawWidth = modelSizeToPixel(
                output[0][2][i]
            )

            val rawHeight = modelSizeToPixel(
                output[0][3][i]
            )

            val sourceCenterX = (
                    (rawCenterX - letterbox.padX) /
                            letterbox.scale
                    ) / letterbox.sourceWidth

            val sourceCenterY = (
                    (rawCenterY - letterbox.padY) /
                            letterbox.scale
                    ) / letterbox.sourceHeight

            val sourceWidth = (
                    rawWidth /
                            letterbox.scale
                    ) / letterbox.sourceWidth

            val sourceHeight = (
                    rawHeight /
                            letterbox.scale
                    ) / letterbox.sourceHeight

            var left = sourceCenterX - sourceWidth / 2f
            var right = sourceCenterX + sourceWidth / 2f

            var top = sourceCenterY - sourceHeight / 2f
            var bottom = sourceCenterY + sourceHeight / 2f

            left = left.coerceIn(
                0f,
                1f
            )

            right = right.coerceIn(
                0f,
                1f
            )

            top = top.coerceIn(
                0f,
                1f
            )

            bottom = bottom.coerceIn(
                0f,
                1f
            )

            val clippedWidth = right - left
            val clippedHeight = bottom - top

            if (
                clippedWidth <= 0.02f ||
                clippedHeight <= 0.05f
            ) {
                continue
            }

            val keypoints = ArrayList<PoseKeyPoint>(
                17
            )

            for (
            keyPointIndex in 0 until 17
            ) {

                val base = 5 + keyPointIndex * 3

                val modelX = modelCoordinateToPixel(
                    output[0][base][i]
                )

                val modelY = modelCoordinateToPixel(
                    output[0][base + 1][i]
                )

                val keyPointConfidence =
                    output[0][base + 2][i]

                val sourceX = (
                        (modelX - letterbox.padX) /
                                letterbox.scale
                        ) / letterbox.sourceWidth

                val sourceY = (
                        (modelY - letterbox.padY) /
                                letterbox.scale
                        ) / letterbox.sourceHeight

                keypoints.add(
                    PoseKeyPoint(
                        x = sourceX,
                        y = sourceY,
                        confidence = keyPointConfidence
                    )
                )
            }

            detections.add(
                RawPersonDetection(
                    boundingBox = PoseBoundingBox(
                        centerX = (left + right) / 2f,
                        centerY = (top + bottom) / 2f,
                        width = clippedWidth,
                        height = clippedHeight
                    ),
                    keypoints = keypoints,
                    personConfidence = confidence
                )
            )
        }

        return detections
    }

    private fun modelCoordinateToPixel(
        value: Float
    ): Float {

        return if (
            abs(value) <= 2f
        ) {
            value * INPUT_SIZE
        } else {
            value
        }
    }

    private fun modelSizeToPixel(
        value: Float
    ): Float {

        return if (
            abs(value) <= 2f
        ) {
            value * INPUT_SIZE
        } else {
            value
        }
    }

    // ============================================================
    // NMS
    // ============================================================

    private fun applyNms(
        detections: List<RawPersonDetection>
    ): List<RawPersonDetection> {

        if (
            detections.isEmpty()
        ) {
            return emptyList()
        }

        val sorted = detections.sortedByDescending {
            it.personConfidence
        }

        val selected =
            mutableListOf<RawPersonDetection>()

        for (candidate in sorted) {

            var suppressed = false

            for (existing in selected) {

                if (
                    calculateIoU(
                        candidate.boundingBox,
                        existing.boundingBox
                    ) > NMS_IOU_THRESHOLD
                ) {
                    suppressed = true
                    break
                }
            }

            if (
                !suppressed
            ) {
                selected.add(
                    candidate
                )
            }

            if (
                selected.size >=
                MAX_PERSON_COUNT
            ) {
                break
            }
        }

        return selected
    }

    private fun calculateIoU(
        first: PoseBoundingBox,
        second: PoseBoundingBox
    ): Float {

        val firstLeft =
            first.centerX - first.width / 2f

        val firstRight =
            first.centerX + first.width / 2f

        val firstTop =
            first.centerY - first.height / 2f

        val firstBottom =
            first.centerY + first.height / 2f

        val secondLeft =
            second.centerX - second.width / 2f

        val secondRight =
            second.centerX + second.width / 2f

        val secondTop =
            second.centerY - second.height / 2f

        val secondBottom =
            second.centerY + second.height / 2f

        val intersectionLeft = maxOf(
            firstLeft,
            secondLeft
        )

        val intersectionRight = minOf(
            firstRight,
            secondRight
        )

        val intersectionTop = maxOf(
            firstTop,
            secondTop
        )

        val intersectionBottom = minOf(
            firstBottom,
            secondBottom
        )

        val intersectionWidth = maxOf(
            0f,
            intersectionRight - intersectionLeft
        )

        val intersectionHeight = maxOf(
            0f,
            intersectionBottom - intersectionTop
        )

        val intersectionArea =
            intersectionWidth * intersectionHeight

        val firstArea =
            first.width * first.height

        val secondArea =
            second.width * second.height

        val union =
            firstArea +
                    secondArea -
                    intersectionArea

        if (
            union <= 0f
        ) {
            return 0f
        }

        return intersectionArea / union
    }

    fun close() {
        interpreter.close()
    }
}