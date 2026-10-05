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
import kotlin.math.ceil

class MainActivity :
    AppCompatActivity() {

    companion object {

        private const val TAG =
            "BeOdysseusPose"

        private const val INFERENCE_INTERVAL_MS =
            250L

        private const val TURNING_DURATION_MS =
            3000L
    }

    private lateinit var previewView:
            PreviewView

    private lateinit var poseOverlayView:
            PoseOverlayView

    private lateinit var userStatusText:
            TextView

    private lateinit var shoulderTiltText:
            TextView

    private lateinit var bodyLeanText:
            TextView

    private lateinit var armAlignmentText:
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

    private var measurementPhase =
        MeasurementPhase.WAITING_FRONT

    private var turningStartTime =
        0L

    /*
     * SIDE_MEASURING 진입 후
     * 측면 프로필 재등록이 아직 안 됐는지 표시.
     */
    private var sideProfileRefreshPending =
        false

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
                granted ->

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

        shoulderTiltText =
            findViewById(
                R.id.shoulderTiltText
            )

        bodyLeanText =
            findViewById(
                R.id.bodyLeanText
            )

        armAlignmentText =
            findViewById(
                R.id.armAlignmentText
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
            ) {
                    imageProxy ->

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

        // 1. YOLO26n-Pose
        val detections =
            poseModelLoader.runInference(
                bitmap
            )

        // 2. 기존 USER 01 Tracker
        val trackerUpdate =
            poseTracker.update(
                detections,
                bitmap
            )

        // 3. 측정 단계 관리
        updateMeasurementPhase(
            trackerUpdate.state
        )

        /*
         * 측면 측정 단계 진입 후
         * USER 01이 안정적으로 잡힌 첫 프레임에서
         * 현재 측면 모습을 새로운 추적 프로필로 저장.
         */
        if (
            measurementPhase ==
            MeasurementPhase.SIDE_MEASURING &&
            sideProfileRefreshPending &&
            trackerUpdate.state ==
            TrackingState.LOCKED &&
            trackerUpdate.detection !=
            null
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

        /*
         * 추적이 완전히 끊긴 경우
         * 이전 Skeleton 좌표 제거.
         */
        if (
            trackerUpdate.detection ==
            null &&
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

        // 4. Skeleton smoothing
        val smoothedDetection =
            trackerUpdate.detection?.let {

                poseSmoother.smooth(
                    it
                )
            }

        /*
         * 5. 자세 지표
         *
         * SIDE_MEASURING일 때만 계산.
         *
         * PoseMetrics는 아직 기존 버전 유지.
         */
        val trackedPose =
            smoothedDetection?.let {
                    detection ->

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

                TrackedPose(
                    boundingBox =
                        detection.boundingBox,

                    keypoints =
                        detection.keypoints,

                    personConfidence =
                        detection.personConfidence,

                    shoulderTiltDegree =
                        metrics.shoulderTiltDegree,

                    bodyLeanDegree =
                        metrics.bodyLeanDegree,

                    armAlignmentDegree =
                        metrics.armAlignmentDegree,

                    leftElbowAngleDegree =
                        metrics.leftElbowAngleDegree,

                    rightElbowAngleDegree =
                        metrics.rightElbowAngleDegree
                )
            }

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

            MeasurementPhase.WAITING_FRONT -> {

                /*
                 * 기존 USER 01 정면 등록 완료.
                 */
                if (
                    trackingState ==
                    TrackingState.LOCKED
                ) {

                    measurementPhase =
                        MeasurementPhase.TURNING

                    turningStartTime =
                        now

                    /*
                     * 회전하면서 정면 프로필과
                     * 차이가 커질 것을 고려해
                     * Tracker를 측면 전환 모드로 변경.
                     */
                    poseTracker.beginSideTransitionMode()

                    Log.d(
                        TAG,
                        "MEASUREMENT: TURNING START"
                    )
                }
            }

            MeasurementPhase.TURNING -> {

                if (
                    now -
                    turningStartTime >=
                    TURNING_DURATION_MS
                ) {

                    measurementPhase =
                        MeasurementPhase.SIDE_MEASURING

                    /*
                     * 다음 안정적인 LOCKED 프레임에서
                     * 측면 프로필을 저장.
                     */
                    sideProfileRefreshPending =
                        true

                    poseSmoother.reset()

                    Log.d(
                        TAG,
                        "MEASUREMENT: SIDE MEASURING START"
                    )
                }
            }

            MeasurementPhase.SIDE_MEASURING -> {
                // 계속 실시간 측정
            }
        }
    }

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

                /*
                 * 측면 측정 중 잠깐 관절을 놓친 경우에는
                 * 다시 USER 등록하는 것처럼 보이지 않도록 표시.
                 */
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

            MeasurementPhase.WAITING_FRONT -> {

                userStatusText.text =
                    "USER 01 · LOCKED"

                clearMetricUI()
            }

            MeasurementPhase.TURNING -> {

                userStatusText.text =
                    "측면으로 돌아서 양궁 자세를 취해주세요. ${result.measurementRemainingSeconds}"

                clearMetricUI()
            }

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

        val shoulder =
            pose.shoulderTiltDegree

        val body =
            pose.bodyLeanDegree

        val arm =
            pose.armAlignmentDegree

        shoulderTiltText.text =
            if (
                shoulder !=
                null
            ) {

                String.format(
                    Locale.US,
                    "어깨 기울기: %+.1f°",
                    shoulder
                )

            } else {

                "어깨 기울기: --"
            }

        bodyLeanText.text =
            if (
                body !=
                null
            ) {

                String.format(
                    Locale.US,
                    "상체 기울기: %+.1f°",
                    body
                )

            } else {

                "상체 기울기: --"
            }

        armAlignmentText.text =
            if (
                arm !=
                null
            ) {

                String.format(
                    Locale.US,
                    "팔 정렬: %.1f°",
                    arm
                )

            } else {

                "팔 정렬: --"
            }
    }

    private fun clearMetricUI() {

        shoulderTiltText.text =
            "어깨 기울기: --"

        bodyLeanText.text =
            "상체 기울기: --"

        armAlignmentText.text =
            "팔 정렬: --"
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