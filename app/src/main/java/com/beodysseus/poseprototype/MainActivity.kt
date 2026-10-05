package com.beodysseus.poseprototype

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil

class MainActivity :
    AppCompatActivity() {

    companion object {

        private const val TAG =
            "BeOdysseusPose"

        private const val INFERENCE_INTERVAL_MS =
            250L

        /*
         * 기존 3초 → 5초
         *
         * 사용자가 실제 활을 들고
         * 측면 양궁 자세까지 완성할 시간.
         */
        private const val TURNING_DURATION_MS =
            5000L

        /*
         * 5초 준비 시간의 마지막 1.5초 동안
         * 활팔 기준값을 수집한다.
         */
        private const val BOW_CALIBRATION_WINDOW_MS =
            1500L

        /*
         * 너무 적은 프레임으로
         * 기준을 만들지 않기 위한 최소 샘플 수.
         */
        private const val MIN_BOW_CALIBRATION_SAMPLES =
            3

        private const val MAX_BOW_CALIBRATION_SAMPLES =
            10
    }

    private lateinit var previewView:
            PreviewView

    private lateinit var poseOverlayView:
            PoseOverlayView

    private lateinit var userStatusText:
            TextView

    private lateinit var bodyLeanText:
            TextView

    private lateinit var bowArmText:
            TextView

    private lateinit var drawArmText:
            TextView

    private lateinit var retryUserButton:
            Button

    private lateinit var cameraExecutor:
            ExecutorService

    private lateinit var poseModelLoader:
            PoseModelLoader

    private lateinit var poseTracker:
            PoseTracker

    private val poseSmoother =
        PoseSmoother()

    private val poseMetrics =
        PoseMetrics()

    private var lastInferenceTime =
        0L

    // ============================================================
    // Measurement State
    // ============================================================

    private var measurementPhase =
        MeasurementPhase.WAITING_FRONT

    private var turningStartTime =
        0L

    private var sideProfileRefreshPending =
        false

    // ============================================================
    // 활팔 Calibration
    // ============================================================

    /*
     * 5초 준비시간 마지막 구간의
     * raw 활팔 오차값 저장.
     *
     * 예:
     * 29, 31, 30, 32...
     */
    private val bowCalibrationSamples =
        mutableListOf<Float>()

    /*
     * 실제 측정에 사용할 활팔 기준값.
     *
     * 예:
     * YOLO가 곧게 편 팔을 30°로 본다면
     * baseline = 30°
     */
    private var bowArmBaselineError:
            Float? = null

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (
                granted
            ) {

                startCamera()

            } else {

                Toast.makeText(
                    this,
                    "카메라 권한이 필요합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(
            R.layout.activity_main
        )

        previewView =
            findViewById(
                R.id.previewView
            )

        poseOverlayView =
            findViewById(
                R.id.poseOverlayView
            )

        userStatusText =
            findViewById(
                R.id.userStatusText
            )

        bodyLeanText =
            findViewById(
                R.id.bodyLeanText
            )

        bowArmText =
            findViewById(
                R.id.bowArmText
            )

        drawArmText =
            findViewById(
                R.id.drawArmText
            )

        retryUserButton =
            findViewById(
                R.id.retryUserButton
            )

        previewView.scaleType =
            PreviewView.ScaleType.FILL_CENTER

        poseOverlayView.setMirrorX(
            true
        )

        cameraExecutor =
            Executors.newSingleThreadExecutor()

        poseModelLoader =
            PoseModelLoader(
                this
            )

        val appearanceExtractor =
            AppearanceExtractor()

        poseTracker =
            PoseTracker(
                appearanceExtractor
            )

        setupRetryButton()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // ============================================================
    // Retry
    // ============================================================

    private fun setupRetryButton() {

        retryUserButton.setOnClickListener {

            userStatusText.text =
                "사용자를 화면 중앙에 위치해주세요."

            clearMetricUI()

            poseOverlayView.clear()

            retryUserButton.visibility =
                View.GONE

            measurementPhase =
                MeasurementPhase.WAITING_FRONT

            turningStartTime =
                0L

            sideProfileRefreshPending =
                false

            /*
             * 사용자 다시 등록 시
             * 이전 사람의 보정값도 제거.
             */
            bowCalibrationSamples.clear()

            bowArmBaselineError =
                null

            cameraExecutor.execute {

                poseTracker.restart()

                poseSmoother.reset()
            }
        }
    }

    // ============================================================
    // Camera
    // ============================================================

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                this
            )

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview =
                Preview.Builder()
                    .setTargetAspectRatio(
                        AspectRatio.RATIO_4_3
                    )
                    .build()
                    .also {

                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setTargetAspectRatio(
                        AspectRatio.RATIO_4_3
                    )
                    .setBackpressureStrategy(
                        ImageAnalysis
                            .STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                try {

                    val now =
                        SystemClock.elapsedRealtime()

                    if (
                        now -
                        lastInferenceTime <
                        INFERENCE_INTERVAL_MS
                    ) {

                        return@setAnalyzer
                    }

                    lastInferenceTime =
                        now

                    val originalBitmap =
                        imageProxy.toBitmap()

                    val rotation =
                        imageProxy
                            .imageInfo
                            .rotationDegrees

                    val rotatedBitmap =
                        rotateBitmap(
                            originalBitmap,
                            rotation
                        )

                    try {

                        processPoseFrame(
                            rotatedBitmap
                        )

                    } finally {

                        if (
                            rotatedBitmap !==
                            originalBitmap
                        ) {

                            originalBitmap.recycle()
                        }

                        rotatedBitmap.recycle()
                    }

                } catch (
                    e: Exception
                ) {

                    Log.e(
                        TAG,
                        "Pose frame failed",
                        e
                    )

                } finally {

                    imageProxy.close()
                }
            }

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Camera binding failed",
                    e
                )
            }

        },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    // ============================================================
    // Pose Pipeline
    // ============================================================

    private fun processPoseFrame(
        bitmap: Bitmap
    ) {

        // --------------------------------------------------------
        // 1. YOLO Pose
        // --------------------------------------------------------

        val detections =
            poseModelLoader.runInference(
                bitmap
            )

        // --------------------------------------------------------
        // 2. USER 01 Tracking
        // --------------------------------------------------------

        val trackerUpdate =
            poseTracker.update(
                detections,
                bitmap
            )

        // --------------------------------------------------------
        // 3. Measurement Phase
        // --------------------------------------------------------

        updateMeasurementPhase(
            trackerUpdate.state
        )

        // --------------------------------------------------------
        // 4. 측면 프로필 갱신
        // --------------------------------------------------------

        if (
            measurementPhase ==
            MeasurementPhase.SIDE_MEASURING &&
            sideProfileRefreshPending &&
            trackerUpdate.state ==
            TrackingState.LOCKED &&
            trackerUpdate.detection != null
        ) {

            val refreshed =
                poseTracker.refreshSideProfile(
                    trackerUpdate.detection,
                    bitmap
                )

            if (
                refreshed
            ) {

                sideProfileRefreshPending =
                    false

                poseSmoother.reset()

                Log.d(
                    TAG,
                    "SIDE PROFILE REFRESH COMPLETE"
                )
            }
        }

        // --------------------------------------------------------
        // 5. Tracking 끊김 시 smoothing 초기화
        // --------------------------------------------------------

        if (
            trackerUpdate.detection == null &&
            (
                    trackerUpdate.state ==
                            TrackingState.SEARCHING ||
                            trackerUpdate.state ==
                            TrackingState.RECOVERING ||
                            trackerUpdate.state ==
                            TrackingState.LOST
                    )
        ) {

            poseSmoother.reset()
        }

        // --------------------------------------------------------
        // 6. Skeleton smoothing
        // --------------------------------------------------------

        val smoothedDetection =
            trackerUpdate.detection?.let {

                poseSmoother.smooth(
                    it
                )
            }

        // --------------------------------------------------------
        // 7. 활팔 Calibration sample 수집
        // --------------------------------------------------------

        collectBowCalibrationSample(
            trackerUpdate.state,
            smoothedDetection
        )

        // --------------------------------------------------------
        // 8. 최종 자세 지표
        // --------------------------------------------------------

        val trackedPose =
            smoothedDetection?.let { detection ->

                val metrics =
                    if (
                        measurementPhase ==
                        MeasurementPhase.SIDE_MEASURING &&
                        trackerUpdate.state ==
                        TrackingState.LOCKED
                    ) {

                        poseMetrics.calculate(
                            detection
                        )

                    } else {

                        PoseMetricsResult()
                    }

                /*
                 * PoseMetrics의 활팔 값은 아직
                 * YOLO raw 기준 오차이다.
                 *
                 * 예:
                 * 곧게 폈는데 30°
                 *
                 * 여기서 calibration 기준을 빼
                 * 사용자 기준 오차로 바꾼다.
                 */
                val calibratedBowError =
                    calculateCalibratedBowError(
                        metrics.bowArmStraightnessErrorDegree
                    )

                TrackedPose(

                    boundingBox =
                        detection.boundingBox,

                    keypoints =
                        detection.keypoints,

                    personConfidence =
                        detection.personConfidence,

                    shoulderTiltDegree =
                        metrics.shoulderTiltDegree,

                    armAlignmentDegree =
                        metrics.armAlignmentDegree,

                    bodyLeanDegree =
                        metrics.bodyLeanDegree,

                    /*
                     * UI 및 이후 Stage 저장에는
                     * 보정된 값을 사용.
                     */
                    bowArmStraightnessErrorDegree =
                        calibratedBowError,

                    drawArmElbowAngleDegree =
                        metrics.drawArmElbowAngleDegree,

                    bowArmSide =
                        metrics.bowArmSide,

                    drawArmSide =
                        metrics.drawArmSide,

                    leftElbowAngleDegree =
                        metrics.leftElbowAngleDegree,

                    rightElbowAngleDegree =
                        metrics.rightElbowAngleDegree
                )
            }

        // --------------------------------------------------------
        // 9. Frame Result
        // --------------------------------------------------------

        val frameResult =
            PoseFrameResult(

                state =
                    trackerUpdate.state,

                remainingSeconds =
                    trackerUpdate.remainingSeconds,

                measurementPhase =
                    measurementPhase,

                measurementRemainingSeconds =
                    getTurningRemainingSeconds(),

                pose =
                    trackedPose,

                sourceWidth =
                    bitmap.width,

                sourceHeight =
                    bitmap.height
            )

        runOnUiThread {

            poseOverlayView.updateFrame(
                frameResult
            )

            updateUI(
                frameResult
            )
        }
    }

    // ============================================================
    // Measurement Phase
    // ============================================================

    private fun updateMeasurementPhase(
        trackingState: TrackingState
    ) {

        val now =
            SystemClock.elapsedRealtime()

        when (
            measurementPhase
        ) {

            // ----------------------------------------------------
            // 정면 USER 등록 대기
            // ----------------------------------------------------

            MeasurementPhase.WAITING_FRONT -> {

                if (
                    trackingState ==
                    TrackingState.LOCKED
                ) {

                    measurementPhase =
                        MeasurementPhase.TURNING

                    turningStartTime =
                        now

                    /*
                     * 새로운 사용자이므로
                     * 보정값 초기화.
                     */
                    bowCalibrationSamples.clear()

                    bowArmBaselineError =
                        null

                    poseTracker.beginSideTransitionMode()

                    Log.d(
                        TAG,
                        "MEASUREMENT: TURNING START"
                    )
                }
            }

            // ----------------------------------------------------
            // 측면 전환 + 양궁 자세 준비
            // ----------------------------------------------------

            MeasurementPhase.TURNING -> {

                if (
                    now -
                    turningStartTime >=
                    TURNING_DURATION_MS
                ) {

                    /*
                     * 마지막 1.5초 동안 모은 값으로
                     * 활팔 기준값 확정.
                     */
                    finalizeBowCalibration()

                    measurementPhase =
                        MeasurementPhase.SIDE_MEASURING

                    sideProfileRefreshPending =
                        true

                    poseSmoother.reset()

                    Log.d(
                        TAG,
                        "MEASUREMENT: SIDE MEASURING START"
                    )
                }
            }

            // ----------------------------------------------------
            // 측면 실시간 측정
            // ----------------------------------------------------

            MeasurementPhase.SIDE_MEASURING -> {
                // 유지
            }
        }
    }

    // ============================================================
    // Bow Arm Calibration
    // ============================================================

    private fun collectBowCalibrationSample(
        trackingState: TrackingState,
        detection: RawPersonDetection?
    ) {

        if (
            measurementPhase !=
            MeasurementPhase.TURNING
        ) {
            return
        }

        if (
            trackingState !=
            TrackingState.LOCKED
        ) {
            return
        }

        if (
            detection ==
            null
        ) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        val elapsed =
            now -
                    turningStartTime

        /*
         * 마지막 1.5초 전까지는
         * 사용자가 돌아서 자세를 만드는 시간.
         */
        val calibrationStart =
            TURNING_DURATION_MS -
                    BOW_CALIBRATION_WINDOW_MS

        if (
            elapsed <
            calibrationStart
        ) {
            return
        }

        val metrics =
            poseMetrics.calculate(
                detection
            )

        val rawBowError =
            metrics
                .bowArmStraightnessErrorDegree
                ?: return

        /*
         * 비정상적으로 큰 값은
         * calibration에서 제외.
         *
         * 실제 우리가 기대하는
         * 곧게 편 팔의 raw 값은
         * 현재 테스트 기준 약 30° 부근.
         */
        if (
            rawBowError !in
            0f..90f
        ) {
            return
        }

        bowCalibrationSamples.add(
            rawBowError
        )

        if (
            bowCalibrationSamples.size >
            MAX_BOW_CALIBRATION_SAMPLES
        ) {

            bowCalibrationSamples.removeAt(
                0
            )
        }

        Log.d(
            TAG,
            "BOW CALIBRATION SAMPLE: $rawBowError"
        )
    }

    private fun finalizeBowCalibration() {

        if (
            bowCalibrationSamples.size <
            MIN_BOW_CALIBRATION_SAMPLES
        ) {

            /*
             * 충분히 못 모은 경우
             * SIDE_MEASURING 첫 정상 프레임에서
             * fallback으로 기준을 잡는다.
             */
            bowArmBaselineError =
                null

            Log.w(
                TAG,
                "BOW CALIBRATION: NOT ENOUGH SAMPLES (${bowCalibrationSamples.size})"
            )

            return
        }

        /*
         * 평균보다 중앙값을 사용.
         *
         * YOLO가 한 프레임 튀더라도
         * 기준값에 영향이 적다.
         */
        val sorted =
            bowCalibrationSamples.sorted()

        val middle =
            sorted.size /
                    2

        bowArmBaselineError =
            if (
                sorted.size % 2 ==
                0
            ) {

                (
                        sorted[middle - 1] +
                                sorted[middle]
                        ) /
                        2f

            } else {

                sorted[middle]
            }

        Log.d(
            TAG,
            "BOW CALIBRATION COMPLETE: baseline=$bowArmBaselineError, samples=${bowCalibrationSamples.size}"
        )
    }

    private fun calculateCalibratedBowError(
        rawBowError: Float?
    ): Float? {

        if (
            rawBowError ==
            null
        ) {
            return null
        }

        /*
         * 준비 시간에서 기준값을 못 잡은 경우
         * 측면 측정의 첫 정상 프레임을 fallback으로 사용.
         */
        if (
            bowArmBaselineError ==
            null
        ) {

            bowArmBaselineError =
                rawBowError

            Log.w(
                TAG,
                "BOW CALIBRATION FALLBACK: baseline=$rawBowError"
            )

            return 0f
        }

        /*
         * 최종 활팔 오차
         *
         * 예:
         *
         * baseline = 30°
         *
         * 현재 31°
         * → 1°
         *
         * 현재 110°
         * → 80°
         */
        return abs(
            rawBowError -
                    bowArmBaselineError!!
        )
    }

    // ============================================================
    // Timer
    // ============================================================

    private fun getTurningRemainingSeconds():
            Int {

        if (
            measurementPhase !=
            MeasurementPhase.TURNING
        ) {
            return 0
        }

        val now =
            SystemClock.elapsedRealtime()

        val remaining =
            TURNING_DURATION_MS -
                    (
                            now -
                                    turningStartTime
                            )

        if (
            remaining <=
            0
        ) {
            return 0
        }

        return ceil(
            remaining /
                    1000.0
        ).toInt()
    }

    // ============================================================
    // UI
    // ============================================================

    private fun updateUI(
        result: PoseFrameResult
    ) {

        when (
            result.state
        ) {

            TrackingState.SEARCHING -> {

                userStatusText.text =
                    "사용자를 화면 중앙에 위치해주세요."

                clearMetricUI()

                retryUserButton.visibility =
                    View.GONE

                return
            }

            TrackingState.REGISTERING -> {

                userStatusText.text =
                    "정면을 바라봐 주세요. 사용자 인식 중... ${result.remainingSeconds}"

                clearMetricUI()

                retryUserButton.visibility =
                    View.GONE

                return
            }

            TrackingState.RECOVERING -> {

                if (
                    result.measurementPhase ==
                    MeasurementPhase.SIDE_MEASURING
                ) {

                    userStatusText.text =
                        "측면 자세를 유지해주세요."

                } else {

                    userStatusText.text =
                        "USER 01 다시 찾는 중..."
                }

                clearMetricUI()

                retryUserButton.visibility =
                    View.GONE

                return
            }

            TrackingState.LOST -> {

                userStatusText.text =
                    "사용자를 찾을 수 없습니다."

                clearMetricUI()

                retryUserButton.visibility =
                    View.VISIBLE

                return
            }

            TrackingState.LOCKED -> {
                // MeasurementPhase에서 처리
            }
        }

        when (
            result.measurementPhase
        ) {

            // ----------------------------------------------------
            // USER 등록 완료 전
            // ----------------------------------------------------

            MeasurementPhase.WAITING_FRONT -> {

                userStatusText.text =
                    "USER 01 · LOCKED"

                clearMetricUI()
            }

            // ----------------------------------------------------
            // 측면 양궁 자세 준비
            // ----------------------------------------------------

            MeasurementPhase.TURNING -> {

                if (
                    result.measurementRemainingSeconds <=
                    1
                ) {

                    /*
                     * 마지막 1초는 움직이지 않고
                     * 실제 양궁 자세 유지.
                     */
                    userStatusText.text =
                        "양궁 자세를 유지해주세요. 활팔 기준 보정 중..."

                } else {

                    userStatusText.text =
                        "활을 들고 측면 양궁 자세를 취해주세요. ${result.measurementRemainingSeconds}"
                }

                clearMetricUI()
            }

            // ----------------------------------------------------
            // 실시간 측정
            // ----------------------------------------------------

            MeasurementPhase.SIDE_MEASURING -> {

                if (
                    result.pose !=
                    null
                ) {

                    userStatusText.text =
                        "측면 자세 측정 중"

                } else {

                    userStatusText.text =
                        "측면 자세를 유지해주세요."
                }

                updateMetricUI(
                    result.pose
                )
            }
        }

        retryUserButton.visibility =
            View.VISIBLE
    }

    // ============================================================
    // Metric UI
    // ============================================================

    private fun updateMetricUI(
        pose: TrackedPose?
    ) {

        if (
            pose ==
            null
        ) {

            clearMetricUI()

            return
        }

        val bodyLean =
            pose.bodyLeanDegree

        val bowError =
            pose
                .bowArmStraightnessErrorDegree

        val drawError =
            pose.drawArmElbowAngleDegree

        bodyLeanText.text =
            if (
                bodyLean !=
                null
            ) {

                String.format(
                    Locale.US,
                    "상체 기울기: %.1f°",
                    bodyLean
                )

            } else {

                "상체 기울기: --"
            }

        bowArmText.text =
            if (
                bowError !=
                null
            ) {

                String.format(
                    Locale.US,
                    "활팔 기준 오차: %.1f°",
                    bowError
                )

            } else {

                "활팔 기준 오차: --"
            }

        drawArmText.text =
            if (
                drawError !=
                null
            ) {

                String.format(
                    Locale.US,
                    "당김팔 정렬 오차: %.1f°",
                    drawError
                )

            } else {

                "당김팔 정렬 오차: --"
            }
    }

    private fun clearMetricUI() {

        bodyLeanText.text =
            "상체 기울기: --"

        bowArmText.text =
            "활팔 기준 오차: --"

        drawArmText.text =
            "당김팔 정렬 오차: --"
    }

    // ============================================================
    // Bitmap
    // ============================================================

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        if (
            rotationDegrees ==
            0
        ) {
            return bitmap
        }

        val matrix =
            Matrix()

        matrix.postRotate(
            rotationDegrees.toFloat()
        )

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()

        poseModelLoader.close()
    }
}