package com.beodysseus.poseprototype

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AppearanceDescriptor(
    val values: FloatArray
)

class AppearanceExtractor {

    companion object {
        private const val KEYPOINT_THRESHOLD = 0.35f
        private const val HISTOGRAM_SIZE = 64
    }

    fun extract(
        bitmap: Bitmap,
        detection: RawPersonDetection
    ): AppearanceDescriptor? {

        val region = calculateTorsoRegion(
            detection
        ) ?: return null

        val left = (
                region.left *
                        bitmap.width
                ).toInt().coerceIn(
                0,
                bitmap.width - 1
            )

        val right = (
                region.right *
                        bitmap.width
                ).toInt().coerceIn(
                left + 1,
                bitmap.width
            )

        val top = (
                region.top *
                        bitmap.height
                ).toInt().coerceIn(
                0,
                bitmap.height - 1
            )

        val bottom = (
                region.bottom *
                        bitmap.height
                ).toInt().coerceIn(
                top + 1,
                bitmap.height
            )

        val regionWidth =
            right - left

        val regionHeight =
            bottom - top

        if (
            regionWidth < 5 ||
            regionHeight < 5
        ) {
            return null
        }

        val histogram =
            FloatArray(HISTOGRAM_SIZE)

        val step = max(
            1,
            min(
                regionWidth,
                regionHeight
            ) / 25
        )

        var sampleCount = 0

        var y = top

        while (
            y < bottom
        ) {

            var x = left

            while (
                x < right
            ) {

                val pixel =
                    bitmap.getPixel(x, y)

                val r = Color.red(
                    pixel
                )

                val g = Color.green(
                    pixel
                )

                val b = Color.blue(
                    pixel
                )

                val rBin = (
                        r / 64
                        ).coerceIn(
                        0,
                        3
                    )

                val gBin = (
                        g / 64
                        ).coerceIn(
                        0,
                        3
                    )

                val bBin = (
                        b / 64
                        ).coerceIn(
                        0,
                        3
                    )

                val index =
                    rBin * 16 +
                            gBin * 4 +
                            bBin

                histogram[index] += 1f

                sampleCount++

                x += step
            }

            y += step
        }

        if (
            sampleCount == 0
        ) {
            return null
        }

        normalizeHistogram(
            histogram
        )

        return AppearanceDescriptor(
            histogram
        )
    }

    fun similarity(
        first: AppearanceDescriptor,
        second: AppearanceDescriptor
    ): Float {

        var dot = 0f

        for (
        i in first.values.indices
        ) {
            dot +=
                first.values[i] *
                        second.values[i]
        }

        return dot.coerceIn(
            0f,
            1f
        )
    }

    fun blend(
        old: AppearanceDescriptor?,
        new: AppearanceDescriptor,
        alpha: Float
    ): AppearanceDescriptor {

        if (
            old == null
        ) {
            return AppearanceDescriptor(
                new.values.copyOf()
            )
        }

        val values = FloatArray(
            HISTOGRAM_SIZE
        )

        for (
        i in values.indices
        ) {

            values[i] =
                old.values[i] *
                        (1f - alpha) +
                        new.values[i] *
                        alpha
        }

        normalizeHistogram(
            values
        )

        return AppearanceDescriptor(
            values
        )
    }

    private fun calculateTorsoRegion(
        detection: RawPersonDetection
    ): NormalizedRect? {

        val keypoints =
            detection.keypoints

        if (
            keypoints.size < 13
        ) {
            return fallbackTorsoRegion(
                detection
            )
        }

        val leftShoulder =
            keypoints[5]

        val rightShoulder =
            keypoints[6]

        val leftHip =
            keypoints[11]

        val rightHip =
            keypoints[12]

        val reliable =
            leftShoulder.confidence >=
                    KEYPOINT_THRESHOLD &&
                    rightShoulder.confidence >=
                    KEYPOINT_THRESHOLD &&
                    leftHip.confidence >=
                    KEYPOINT_THRESHOLD &&
                    rightHip.confidence >=
                    KEYPOINT_THRESHOLD

        if (
            !reliable
        ) {
            return fallbackTorsoRegion(
                detection
            )
        }

        val minX = minOf(
            leftShoulder.x,
            rightShoulder.x,
            leftHip.x,
            rightHip.x
        )

        val maxX = maxOf(
            leftShoulder.x,
            rightShoulder.x,
            leftHip.x,
            rightHip.x
        )

        val minY = minOf(
            leftShoulder.y,
            rightShoulder.y
        )

        val maxY = maxOf(
            leftHip.y,
            rightHip.y
        )

        val width =
            maxX - minX

        val height =
            maxY - minY

        if (
            width <= 0f ||
            height <= 0f
        ) {
            return fallbackTorsoRegion(
                detection
            )
        }

        return NormalizedRect(
            left = (
                    minX - width * 0.05f
                    ).coerceIn(
                    0f,
                    1f
                ),

            right = (
                    maxX + width * 0.05f
                    ).coerceIn(
                    0f,
                    1f
                ),

            top = (
                    minY + height * 0.10f
                    ).coerceIn(
                    0f,
                    1f
                ),

            bottom = (
                    maxY - height * 0.10f
                    ).coerceIn(
                    0f,
                    1f
                )
        )
    }

    private fun fallbackTorsoRegion(
        detection: RawPersonDetection
    ): NormalizedRect? {

        val box =
            detection.boundingBox

        val left = (
                box.centerX -
                        box.width * 0.22f
                ).coerceIn(
                0f,
                1f
            )

        val right = (
                box.centerX +
                        box.width * 0.22f
                ).coerceIn(
                0f,
                1f
            )

        val top = (
                box.centerY -
                        box.height * 0.15f
                ).coerceIn(
                0f,
                1f
            )

        val bottom = (
                box.centerY +
                        box.height * 0.20f
                ).coerceIn(
                0f,
                1f
            )

        if (
            right <= left ||
            bottom <= top
        ) {
            return null
        }

        return NormalizedRect(
            left,
            top,
            right,
            bottom
        )
    }

    private fun normalizeHistogram(
        histogram: FloatArray
    ) {

        var length = 0f

        for (
        value in histogram
        ) {
            length += value * value
        }

        length = sqrt(
            length
        )

        if (
            length <= 0f
        ) {
            return
        }

        for (
        i in histogram.indices
        ) {
            histogram[i] /= length
        }
    }

    private data class NormalizedRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}