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

class MainActivity :
    AppCompatActivity() {

    companion object {
        private const val TAG =
            "BeOdysseusPose"

        /*
         * 모델 추론 시간이 약 200ms대이므로
         * 지나친 중복 호출 방지.
         */
        private const val INFERENCE_INTERVAL_MS =
            250L
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

        /*
         * Overlay 좌표 계산과 동일하게 맞춤.
         */
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

    private fun setupRetryButton() {

        retryUserButton.setOnClickListener {

            userStatusText.text =
                "사용자를 화면 중앙에 위치해주세요."

            clearMetricUI()

            poseOverlayView.clear()

            retryUserButton.visibility =
                View.GONE

            cameraExecutor.execute {

                poseTracker.restart()

                poseSmoother.reset()
            }
        }
    }

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

    private fun processPoseFrame(
        bitmap: Bitmap
    ) {

        /*
         * 1. YOLO26n-Pose
         *
         * 화면에 존재하는 모든 사람 검출
         */
        val detections =
            poseModelLoader.runInference(
                bitmap
            )

        /*
         * 2. USER 01 선정/추적
         */
        val trackerUpdate =
            poseTracker.update(
                detections,
                bitmap
            )

        /*
         * 추적이 완전히 끊긴 상태에서는
         * 이전 smoothing 값 제거.
         */
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

        /*
         * 3. USER 01 Skeleton smoothing
         */
        val smoothedDetection =
            trackerUpdate.detection?.let {

                poseSmoother.smooth(
                    it
                )
            }

        /*
         * 4. 자세 지표 계산
         */
        val trackedPose =
            smoothedDetection?.let {
                    detection ->

                val metrics =
                    poseMetrics.calculate(
                        detection
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

    private fun updateUI(
        result: PoseFrameResult
    ) {

        when (
            result.state
        ) {

            TrackingState.SEARCHING -> {

                userStatusText.text =
                    "사용자를 화면 중앙에 위치해주세요."

                retryUserButton.visibility =
                    View.GONE
            }

            TrackingState.REGISTERING -> {

                userStatusText.text =
                    "사용자 인식 중... ${result.remainingSeconds}"

                retryUserButton.visibility =
                    View.GONE
            }

            TrackingState.LOCKED -> {

                if (
                    result.pose != null
                ) {

                    userStatusText.text =
                        "USER 01 · LOCKED"

                } else {

                    userStatusText.text =
                        "사용자 추적 중..."
                }

                /*
                 * 잘못 잠겼을 경우를 위한
                 * fallback 재인식.
                 */
                retryUserButton.visibility =
                    View.VISIBLE
            }

            TrackingState.RECOVERING -> {

                userStatusText.text =
                    "사용자 다시 찾는 중..."

                retryUserButton.visibility =
                    View.GONE
            }

            TrackingState.LOST -> {

                userStatusText.text =
                    "사용자를 찾을 수 없습니다."

                retryUserButton.visibility =
                    View.VISIBLE
            }
        }

        updateMetricUI(
            result.pose
        )
    }

    private fun updateMetricUI(
        pose: TrackedPose?
    ) {

        if (
            pose == null
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
                shoulder != null
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
                body != null
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
                arm != null
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

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        if (
            rotationDegrees == 0
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