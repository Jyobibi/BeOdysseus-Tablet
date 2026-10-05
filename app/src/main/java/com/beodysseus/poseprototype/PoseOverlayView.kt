package com.beodysseus.poseprototype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(
    context,
    attrs
) {

    companion object {
        private const val KEYPOINT_CONFIDENCE =
            0.40f
    }

    private var frameResult:
            PoseFrameResult? = null

    private var mirrorX =
        true

    private val skeletonPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.rgb(
                    50,
                    220,
                    120
                )

            strokeWidth =
                dp(3f)

            style =
                Paint.Style.STROKE

            strokeCap =
                Paint.Cap.ROUND
        }

    private val pointPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.WHITE

            style =
                Paint.Style.FILL
        }

    private val boxPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.rgb(
                    50,
                    220,
                    120
                )

            strokeWidth =
                dp(3f)

            style =
                Paint.Style.STROKE
        }

    private val registeringBoxPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.rgb(
                    255,
                    190,
                    60
                )

            strokeWidth =
                dp(3f)

            style =
                Paint.Style.STROKE
        }

    private val labelBackgroundPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.argb(
                    215,
                    17,
                    24,
                    39
                )

            style =
                Paint.Style.FILL
        }

    private val labelPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.WHITE
            textSize = dp(16f)
            isFakeBoldText = true
        }

    /*
     * COCO 17 keypoints
     */
    private val skeletonEdges =
        arrayOf(
            intArrayOf(0, 1),
            intArrayOf(0, 2),
            intArrayOf(1, 3),
            intArrayOf(2, 4),

            intArrayOf(5, 6),

            intArrayOf(5, 7),
            intArrayOf(7, 9),

            intArrayOf(6, 8),
            intArrayOf(8, 10),

            intArrayOf(5, 11),
            intArrayOf(6, 12),

            intArrayOf(11, 12),

            intArrayOf(11, 13),
            intArrayOf(13, 15),

            intArrayOf(12, 14),
            intArrayOf(14, 16)
        )

    fun setMirrorX(
        enabled: Boolean
    ) {
        mirrorX =
            enabled

        invalidate()
    }

    fun updateFrame(
        result: PoseFrameResult
    ) {

        frameResult =
            result

        invalidate()
    }

    fun clear() {

        frameResult =
            null

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(
            canvas
        )

        val frame =
            frameResult
                ?: return

        val pose =
            frame.pose
                ?: return

        if (
            frame.sourceWidth <= 0 ||
            frame.sourceHeight <= 0
        ) {
            return
        }

        val paint =
            if (
                frame.state ==
                TrackingState.REGISTERING
            ) {

                registeringBoxPaint

            } else {

                boxPaint
            }

        drawBoundingBox(
            canvas,
            frame,
            pose,
            paint
        )

        drawSkeleton(
            canvas,
            frame,
            pose
        )

        drawLabel(
            canvas,
            frame,
            pose
        )
    }

    private fun drawBoundingBox(
        canvas: Canvas,
        frame: PoseFrameResult,
        pose: TrackedPose,
        paint: Paint
    ) {

        val box =
            pose.boundingBox

        val left =
            box.centerX -
                    box.width / 2f

        val right =
            box.centerX +
                    box.width / 2f

        val top =
            box.centerY -
                    box.height / 2f

        val bottom =
            box.centerY +
                    box.height / 2f

        val topLeft =
            mapPoint(
                left,
                top,
                frame
            )

        val bottomRight =
            mapPoint(
                right,
                bottom,
                frame
            )

        val rect =
            RectF(
                min(
                    topLeft.x,
                    bottomRight.x
                ),

                min(
                    topLeft.y,
                    bottomRight.y
                ),

                max(
                    topLeft.x,
                    bottomRight.x
                ),

                max(
                    topLeft.y,
                    bottomRight.y
                )
            )

        canvas.drawRect(
            rect,
            paint
        )
    }

    private fun drawSkeleton(
        canvas: Canvas,
        frame: PoseFrameResult,
        pose: TrackedPose
    ) {

        val keypoints =
            pose.keypoints

        for (
        edge in skeletonEdges
        ) {

            val firstIndex =
                edge[0]

            val secondIndex =
                edge[1]

            if (
                firstIndex !in
                keypoints.indices ||
                secondIndex !in
                keypoints.indices
            ) {
                continue
            }

            val first =
                keypoints[firstIndex]

            val second =
                keypoints[secondIndex]

            if (
                first.confidence <
                KEYPOINT_CONFIDENCE ||
                second.confidence <
                KEYPOINT_CONFIDENCE
            ) {
                continue
            }

            val firstPoint =
                mapPoint(
                    first.x,
                    first.y,
                    frame
                )

            val secondPoint =
                mapPoint(
                    second.x,
                    second.y,
                    frame
                )

            canvas.drawLine(
                firstPoint.x,
                firstPoint.y,
                secondPoint.x,
                secondPoint.y,
                skeletonPaint
            )
        }

        for (
        keypoint in keypoints
        ) {

            if (
                keypoint.confidence <
                KEYPOINT_CONFIDENCE
            ) {
                continue
            }

            val point =
                mapPoint(
                    keypoint.x,
                    keypoint.y,
                    frame
                )

            canvas.drawCircle(
                point.x,
                point.y,
                dp(4.5f),
                pointPaint
            )
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        frame: PoseFrameResult,
        pose: TrackedPose
    ) {

        val text =
            when (
                frame.state
            ) {

                TrackingState.REGISTERING ->
                    "USER DETECTED · ${frame.remainingSeconds}s"

                TrackingState.LOCKED ->
                    "USER 01 · LOCKED"

                else ->
                    return
            }

        val box =
            pose.boundingBox

        val left =
            box.centerX -
                    box.width / 2f

        val top =
            box.centerY -
                    box.height / 2f

        val mapped =
            mapPoint(
                left,
                top,
                frame
            )

        val textWidth =
            labelPaint.measureText(
                text
            )

        val labelHeight =
            dp(28f)

        val padding =
            dp(8f)

        val labelLeft =
            mapped.x.coerceIn(
                0f,
                max(
                    0f,
                    width -
                            textWidth -
                            padding * 2f
                )
            )

        var labelBottom =
            mapped.y -
                    dp(4f)

        if (
            labelBottom <
            labelHeight
        ) {

            labelBottom =
                mapped.y +
                        labelHeight +
                        dp(4f)
        }

        val background =
            RectF(
                labelLeft,
                labelBottom -
                        labelHeight,

                labelLeft +
                        textWidth +
                        padding * 2f,

                labelBottom
            )

        canvas.drawRoundRect(
            background,
            dp(6f),
            dp(6f),
            labelBackgroundPaint
        )

        canvas.drawText(
            text,
            labelLeft +
                    padding,

            labelBottom -
                    dp(7f),

            labelPaint
        )
    }

    /*
     * PreviewView.ScaleType.FILL_CENTER와 동일한 방식으로
     * Analysis 이미지 좌표를 화면 좌표로 변환.
     *
     * 전면카메라 Preview는 좌우 반전되므로
     * mirrorX 적용.
     */
    private fun mapPoint(
        normalizedX: Float,
        normalizedY: Float,
        frame: PoseFrameResult
    ): PointF {

        var x =
            normalizedX

        val y =
            normalizedY

        if (
            mirrorX
        ) {
            x =
                1f - x
        }

        val sourceWidth =
            frame.sourceWidth.toFloat()

        val sourceHeight =
            frame.sourceHeight.toFloat()

        val viewWidth =
            width.toFloat()

        val viewHeight =
            height.toFloat()

        val scale =
            max(
                viewWidth /
                        sourceWidth,

                viewHeight /
                        sourceHeight
            )

        val renderedWidth =
            sourceWidth *
                    scale

        val renderedHeight =
            sourceHeight *
                    scale

        val offsetX =
            (
                    viewWidth -
                            renderedWidth
                    ) / 2f

        val offsetY =
            (
                    viewHeight -
                            renderedHeight
                    ) / 2f

        return PointF(
            x *
                    sourceWidth *
                    scale +
                    offsetX,

            y *
                    sourceHeight *
                    scale +
                    offsetY
        )
    }

    private fun dp(
        value: Float
    ): Float {

        return value *
                resources.displayMetrics.density
    }
}