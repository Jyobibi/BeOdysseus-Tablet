package com.beodysseus.poseprototype

class PoseSmoother {

    companion object {
        private const val POSITION_ALPHA =
            0.42f

        private const val BOX_ALPHA =
            0.40f
    }

    private var previous:
            RawPersonDetection? = null

    fun reset() {
        previous = null
    }

    fun smooth(
        current: RawPersonDetection
    ): RawPersonDetection {

        val old =
            previous

        if (
            old == null ||
            old.keypoints.size !=
            current.keypoints.size
        ) {

            previous =
                current

            return current
        }

        val smoothedKeypoints =
            current.keypoints.mapIndexed {
                    index,
                    currentPoint ->

                val oldPoint =
                    old.keypoints[index]

                if (
                    currentPoint.confidence <
                    0.15f
                ) {

                    currentPoint

                } else {

                    PoseKeyPoint(
                        x = lerp(
                            oldPoint.x,
                            currentPoint.x,
                            POSITION_ALPHA
                        ),

                        y = lerp(
                            oldPoint.y,
                            currentPoint.y,
                            POSITION_ALPHA
                        ),

                        confidence =
                            currentPoint.confidence
                    )
                }
            }

        val oldBox =
            old.boundingBox

        val currentBox =
            current.boundingBox

        val smoothBox =
            PoseBoundingBox(
                centerX =
                    lerp(
                        oldBox.centerX,
                        currentBox.centerX,
                        BOX_ALPHA
                    ),

                centerY =
                    lerp(
                        oldBox.centerY,
                        currentBox.centerY,
                        BOX_ALPHA
                    ),

                width =
                    lerp(
                        oldBox.width,
                        currentBox.width,
                        BOX_ALPHA
                    ),

                height =
                    lerp(
                        oldBox.height,
                        currentBox.height,
                        BOX_ALPHA
                    )
            )

        val result =
            RawPersonDetection(
                boundingBox =
                    smoothBox,

                keypoints =
                    smoothedKeypoints,

                personConfidence =
                    current.personConfidence
            )

        previous =
            result

        return result
    }

    private fun lerp(
        old: Float,
        new: Float,
        alpha: Float
    ): Float {

        return old *
                (1f - alpha) +
                new *
                alpha
    }
}