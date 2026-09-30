package com.beodysseus.poseprototype

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    private var poseModelLoader: PoseModelLoader? = null

    // Prototype용 추론 주기
    private var lastInferenceTime = 0L
    private val inferenceIntervalMs = 300L

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "카메라 권한이 필요합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)

        cameraExecutor =
            Executors.newSingleThreadExecutor()

        poseModelLoader =
            PoseModelLoader(this)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                try {

                    val currentTime =
                        SystemClock.elapsedRealtime()

                    // 300ms마다 한 번씩 추론
                    if (
                        currentTime - lastInferenceTime
                        >= inferenceIntervalMs
                    ) {

                        lastInferenceTime = currentTime

                        val bitmap =
                            imageProxy.toBitmap()

                        val rotation =
                            imageProxy.imageInfo.rotationDegrees

                        val rotatedBitmap =
                            rotateBitmap(
                                bitmap,
                                rotation
                            )

                        poseModelLoader
                            ?.runTestInference(
                                rotatedBitmap
                            )

                        if (rotatedBitmap !== bitmap) {
                            bitmap.recycle()
                        }

                        rotatedBitmap.recycle()
                    }

                } catch (e: Exception) {

                    Log.e(
                        "BeOdysseusPose",
                        "Inference failed",
                        e
                    )

                } finally {

                    imageProxy.close()
                }
            }

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {

                Log.e(
                    "BeOdysseus",
                    "Camera binding failed",
                    e
                )
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        if (rotationDegrees == 0) {
            return bitmap
        }

        val matrix = Matrix()

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

        poseModelLoader?.close()
        cameraExecutor.shutdown()
    }
}