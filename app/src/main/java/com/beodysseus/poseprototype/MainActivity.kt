package com.beodysseus.poseprototype

import android.Manifest
import com.beodysseus.poseprototype.network.NetworkConstants
import com.beodysseus.poseprototype.network.UdpSender
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import com.beodysseus.poseprototype.network.PairingManager
import android.content.Intent
import com.beodysseus.poseprototype.network.NetworkMessageBuilder
import com.beodysseus.poseprototype.result.FinalPostureResultCalculator
import com.beodysseus.poseprototype.network.UdpReceiver
import com.beodysseus.poseprototype.network.NetworkMessageHandler
import com.beodysseus.poseprototype.result.PostureDataCollector
import com.beodysseus.poseprototype.stage.StageManager
import com.beodysseus.poseprototype.tts.PostureTtsManager
import com.beodysseus.poseprototype.feedback.PostureStatus
import com.beodysseus.poseprototype.feedback.PostureFeedbackEvaluator
import com.beodysseus.poseprototype.feedback.PostureWarningTracker
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
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

    private var correctPostureStartTime = 0L
    private val CORRECT_POSTURE_DURATION_MS = 5_000L

    private val pairingManager = PairingManager()

    private lateinit var pairingCodeEditText: EditText
    private lateinit var pairingButton: Button
    private lateinit var pairingStatusText: TextView

    private val processedSeqs = mutableSetOf<Int>()

    private var receivedPairingCode: String? = null

    private var receivedSession: String? = null
    private var phoneIp: String? = null
    private var phonePort: Int = NetworkConstants.PHONE_PORT
    private val udpSender = UdpSender()
    private var outgoingSeq = 1

    private var cameraPermissionGranted = false
    private var cameraStarted = false

    private lateinit var stageStatusText: TextView

    private val finalPostureResultCalculator =
        FinalPostureResultCalculator()

    private val networkMessageHandler =
        NetworkMessageHandler()

    private lateinit var udpReceiver: UdpReceiver

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

    private val postureFeedbackEvaluator =
        PostureFeedbackEvaluator()

    private val bodyWarningTracker =
        PostureWarningTracker()

    private val bowWarningTracker =
        PostureWarningTracker()

    private val drawWarningTracker =
        PostureWarningTracker()

    private lateinit var postureTtsManager: PostureTtsManager

    private val stageManager =
        StageManager()

    private val postureDataCollector =
        PostureDataCollector()

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


    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted

            if (!granted) {

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

        postureTtsManager =
            PostureTtsManager(this)

        udpReceiver =
            UdpReceiver { message, senderIp ->

                val event =
                    networkMessageHandler.parse(message)

                if (event?.type == NetworkConstants.PAIR_OFFER) {

                    receivedPairingCode = event.code
                    receivedSession = event.session
                    phoneIp = senderIp
                    phonePort = event.port ?: NetworkConstants.PHONE_PORT

                    runOnUiThread {
                        pairingStatusText.text =
                            "스마트폰 발견 · 4자리 코드를 입력하세요"
                    }

                    return@UdpReceiver
                }

                if (event?.type == NetworkConstants.PAIR_CONFIRM) {

                    if (
                        event.session == receivedSession &&
                        senderIp == phoneIp
                    ) {
                        runOnUiThread {

                            pairingStatusText.text = "연결 완료"
                            pairingCodeEditText.isEnabled = false
                            pairingButton.isEnabled = false

                            userStatusText.text =
                                "사용자를 화면 중앙에 위치해주세요."

                            if (
                                cameraPermissionGranted &&
                                !cameraStarted
                            ) {
                                cameraStarted = true
                                startCamera()
                            }
                        }
                    }

                    return@UdpReceiver
                }

                if (event?.type == NetworkConstants.PING) {

                    // 현재 연결된 폰과 같은 세션의 ping만 허용
                    if (
                        event.session == receivedSession &&
                        senderIp == phoneIp
                    ) {
                        Log.d(TAG, "PING received")
                    }

                    return@UdpReceiver
                }

                if (event?.type == NetworkConstants.HELLO) {

                    if (
                        event.session == receivedSession &&
                        senderIp == phoneIp
                    ) {
                        Log.d(TAG, "HELLO received")
                    }

                    return@UdpReceiver
                }

                if (event?.type == NetworkConstants.UNPAIR) {

                    if (
                        event.session == receivedSession &&
                        senderIp == phoneIp
                    ) {
                        pairingManager.reset()

                        receivedPairingCode = null
                        receivedSession = null
                        phoneIp = null
                        phonePort = NetworkConstants.PHONE_PORT

                        synchronized(processedSeqs) {
                            processedSeqs.clear()
                        }

                        runOnUiThread {
                            pairingStatusText.text = "스마트폰 연결 대기"
                            pairingCodeEditText.text.clear()
                            pairingCodeEditText.isEnabled = true
                            pairingButton.isEnabled = true
                        }
                    }

                    return@UdpReceiver
                }

                runOnUiThread {
// 페어링 완료 후 게임 패킷 처리
                    if (
                        event != null &&
                        event.type in listOf(
                            NetworkConstants.GAME_START,
                            NetworkConstants.STAGE_START,
                            NetworkConstants.SHOT,
                            NetworkConstants.STAGE_END,
                            NetworkConstants.GAME_END
                        )
                    ) {

                        // 다른 세션의 패킷은 무시
                        if (event.session != receivedSession) {
                            return@runOnUiThread
                        }

                        // ACK는 중복 패킷이어도 다시 전송
                        val targetIp = phoneIp

                        if (targetIp != null) {

                            val ackMessage =
                                NetworkMessageBuilder.createAck(
                                    session = event.session,
                                    seq = outgoingSeq++,
                                    ackSeq = event.seq
                                )

                            udpSender.send(
                                message = ackMessage,
                                targetIp = targetIp,
                                targetPort = phonePort
                            )
                        }

                        // 이미 처리한 seq면 게임 로직은 다시 실행하지 않음
                        synchronized(processedSeqs) {
                            if (!processedSeqs.add(event.seq)) {
                                return@runOnUiThread
                            }
                        }
                    }

                    when (event?.type) {

                        NetworkConstants.GAME_START -> {
                            stageManager.reset()
                            postureDataCollector.reset()

                            synchronized(processedSeqs) {
                                processedSeqs.clear()
                                processedSeqs.add(event.seq)
                            }

                            stageStatusText.text =
                                "USER 01  ·  STAGE 준비"
                        }

                        NetworkConstants.STAGE_START -> {
                            event.stage?.let { stageNumber ->

                                if (stageNumber !in 1..3) {
                                    return@runOnUiThread
                                }

                                stageManager.startStage(stageNumber)

                                bodyWarningTracker.reset()
                                bowWarningTracker.reset()
                                drawWarningTracker.reset()

                                stageStatusText.text =
                                    "USER 01  ·  STAGE ${stageNumber}  ·  MEASURING"
                            }
                        }

                        NetworkConstants.SHOT -> {
                            Log.d(
                                "MainActivity",
                                "SHOT received: stage=${event.stage}, " +
                                        "shotIndex=${event.shotIndex}, " +
                                        "hit=${event.hit}, " +
                                        "accuracy=${event.accuracy}, " +
                                        "stability=${event.stability}"
                            )
                        }

                        NetworkConstants.STAGE_END -> {
                            stageManager.endStage()

                            bodyWarningTracker.reset()
                            bowWarningTracker.reset()
                            drawWarningTracker.reset()

                            stageStatusText.text =
                                "USER 01  ·  STAGE 준비"
                        }

                        NetworkConstants.GAME_END -> {

                            Log.d("MainActivity", "===== GAME_END RECEIVED =====")

                            stageManager.endStage()

                            stageStatusText.text =
                                "USER 01  ·  측정 완료"

                            bodyWarningTracker.reset()
                            bowWarningTracker.reset()
                            drawWarningTracker.reset()

                            val finalResult =
                                finalPostureResultCalculator.calculate(
                                    postureDataCollector
                                )

                            if (finalResult != null) {

                                Log.d(
                                    TAG,
                                    "FINAL RESULT | " +
                                            "overall=${finalResult.overallScore}% | " +
                                            "body=${finalResult.bodyScore}% | " +
                                            "bow=${finalResult.bowArmScore}% | " +
                                            "stage1=${finalResult.stage1Score}% | " +
                                            "stage2=${finalResult.stage2Score}% | " +
                                            "stage3=${finalResult.stage3Score}%"
                                )

                                val intent =
                                    Intent(
                                        this@MainActivity,
                                        ResultActivity::class.java
                                    ).apply {

                                        putExtra(
                                            "overallScore",
                                            finalResult.overallScore
                                        )

                                        putExtra(
                                            "bodyScore",
                                            finalResult.bodyScore
                                        )

                                        putExtra(
                                            "bowArmScore",
                                            finalResult.bowArmScore
                                        )



                                        putExtra(
                                            "stage1Score",
                                            finalResult.stage1Score ?: -1
                                        )

                                        putExtra(
                                            "stage2Score",
                                            finalResult.stage2Score ?: -1
                                        )

                                        putExtra(
                                            "stage3Score",
                                            finalResult.stage3Score ?: -1
                                        )
                                    }

                                udpReceiver.stop()

                                startActivity(intent)

                            } else {

                                Log.d(
                                    TAG,
                                    "FINAL RESULT | 측정 데이터 없음"
                                )
                            }
                        }
                    }
                }
            }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(
            R.layout.activity_main
        )

        stageStatusText =
            findViewById(R.id.stageStatusText)

        pairingCodeEditText =
            findViewById(R.id.pairingCodeEditText)

        pairingButton =
            findViewById(R.id.pairingButton)

        pairingStatusText =
            findViewById(R.id.pairingStatusText)

        pairingButton.setOnClickListener {

            val enteredCode =
                pairingCodeEditText.text.toString().trim()

            val receivedCode = receivedPairingCode
            val session = receivedSession
            val targetIp = phoneIp

            if (
                receivedCode == null ||
                session == null ||
                targetIp == null
            ) {
                pairingStatusText.text = "스마트폰 연결 대기"
                return@setOnClickListener
            }

            if (!pairingManager.pair(receivedCode, enteredCode)) {
                pairingStatusText.text = "코드 불일치"
                return@setOnClickListener
            }

            val message =
                NetworkMessageBuilder.createPairRequest(
                    code = enteredCode,
                    session = session,
                    seq = outgoingSeq++
                )

            udpSender.send(
                message = message,
                targetIp = targetIp,
                targetPort = phonePort
            )

            pairingStatusText.text = "연결 확인 중..."


        }

        udpReceiver.start()



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

            cameraPermissionGranted = true

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
                    metrics.bowArmStraightnessErrorDegree

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

                    drawArmAlignmentErrorDegree =
                        metrics.drawArmAlignmentErrorDegree,

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
                    correctPostureStartTime = 0L
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
                    result.measurementRemainingSeconds <= 1
                ) {
                    userStatusText.text =
                        "측면 자세를 유지해주세요. 잠시 후 측정을 시작합니다."
                } else {
                    userStatusText.text =
                        "측면 자세를 취해주세요. 측정 시작까지 ${result.measurementRemainingSeconds}초"
                }

                clearMetricUI()

                clearMetricUI()
            }

            // ----------------------------------------------------
            // 실시간 측정
            // ----------------------------------------------------

            MeasurementPhase.SIDE_MEASURING -> {

                val pose = result.pose

                if (pose == null) {
                    correctPostureStartTime = 0L

                    userStatusText.text =
                        "측면 자세를 유지해주세요."

                    updateMetricUI(null)
                    return
                }

                val feedback =
                    postureFeedbackEvaluator.evaluate(
                        bodyLeanDegree = pose.bodyLeanDegree,
                        bowArmStraightnessErrorDegree =
                            pose.bowArmStraightnessErrorDegree
                    )

                val allCorrect =
                    feedback.bodyStatus == PostureStatus.NORMAL &&
                            feedback.bowArmStatus == PostureStatus.NORMAL

                if (allCorrect) {

                    if (correctPostureStartTime == 0L) {
                        correctPostureStartTime =
                            SystemClock.elapsedRealtime()
                    }

                    val elapsed =
                        SystemClock.elapsedRealtime() -
                                correctPostureStartTime

                    val remainingSeconds =
                        ((CORRECT_POSTURE_DURATION_MS - elapsed + 999) / 1000)
                            .coerceAtLeast(0)

                    if (remainingSeconds == 0L) {
                        userStatusText.text =
                            "측면 자세 측정 완료"

                        updateMetricUI(pose)
                        return
                    }

                    userStatusText.text =
                        "좋은 자세입니다 · ${remainingSeconds}초 유지해주세요."

                } else {

                    // 하나라도 빨간색이면 5초부터 다시 시작
                    correctPostureStartTime = 0L

                    userStatusText.text =
                        "상체와 활팔 자세를 정상으로 맞춰주세요."
                }

                updateMetricUI(pose)
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

        if (pose == null) {

            bodyWarningTracker.reset()
            bowWarningTracker.reset()
            drawWarningTracker.reset()

            clearMetricUI()
            return
        }

        val bodyLean =
            pose.bodyLeanDegree

        val bowError =
            pose.bowArmStraightnessErrorDegree

        val feedback =
            postureFeedbackEvaluator.evaluate(
                bodyLeanDegree = bodyLean,
                bowArmStraightnessErrorDegree = bowError
            )

        // Stage 진행 중일 때만 자세 데이터를 누적
        if (stageManager.isStageActive) {
            postureDataCollector.addFrame(
                stageNumber = stageManager.currentStage,
                feedback = feedback
            )
        }

        Log.d(
            TAG,
            "POSTURE FEEDBACK | " +
                    "body=${feedback.bodyStatus}, " +
                    "bow=${feedback.bowArmStatus}, "
        )

        val currentTime =
            SystemClock.elapsedRealtime()

        val bodyWarningReady =
            bodyWarningTracker.update(
                feedback.bodyStatus,
                currentTime
            )

        val bowWarningReady =
            bowWarningTracker.update(
                feedback.bowArmStatus,
                currentTime
            )


        if (stageManager.isStageActive) {
            when {
                bodyWarningReady -> {
                    postureTtsManager.speak(
                        "상체 자세를 바로잡아주세요."
                    )
                }

                bowWarningReady -> {
                    postureTtsManager.speak(
                        "왼쪽팔을 곧게 펴주세요."
                    )
                }


            }
        }

        bodyLeanText.text =
            if (bodyLean != null) {

                val statusText =
                    when (feedback.bodyStatus) {
                        PostureStatus.NORMAL -> "✓ 정상"
                        PostureStatus.WARNING -> "! 주의"
                        null -> ""
                    }

                String.format(
                    Locale.US,
                    "상체 기울기: %.1f°  %s",
                    bodyLean,
                    statusText
                )

            } else {

                "상체 기울기: --"
            }

        bowArmText.text =
            if (bowError != null) {

                val statusText =
                    when (feedback.bowArmStatus) {
                        PostureStatus.NORMAL -> "✓ 정상"
                        PostureStatus.WARNING -> "! 주의"
                        null -> ""
                    }

                String.format(
                    Locale.US,
                    "활팔 기준 오차: %.1f°  %s",
                    bowError,
                    statusText
                )

            } else {

                "활팔 기준 오차: --"
            }



        updateMetricCardColor(
            bodyLeanText,
            feedback.bodyStatus
        )

        updateMetricCardColor(
            bowArmText,
            feedback.bowArmStatus
        )

    }

    private fun updateMetricCardColor(
        view: TextView,
        status: PostureStatus?
    ) {
        val color =
            when (status) {
                PostureStatus.NORMAL -> "#1F5C46"
                PostureStatus.WARNING -> "#7F3D3D"
                null -> "#263244"
            }

        view.setBackgroundColor(
            Color.parseColor(color)
        )
    }

    private fun clearMetricUI() {

        bodyLeanText.text =
            "상체 기울기: --"

        bowArmText.text =
            "활팔 기준 오차: --"



        updateMetricCardColor(bodyLeanText, null)
        updateMetricCardColor(bowArmText, null)
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

        udpReceiver.stop()

        postureTtsManager.shutdown()

        cameraExecutor.shutdown()

        poseModelLoader.close()

        super.onDestroy()
    }
}