package com.example.lookawayshield

import android.app.*
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

class ShieldService : Service() {
    private lateinit var wm: WindowManager
    private var shield: View? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastAway = false
    private var awaySince = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, NotificationCompat.Builder(this, "shield")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Look Away Shield")
            .setContentText("On-device gaze protection is active")
            .setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addShield()
        startCamera()
    }

    private fun addShield() {
        val v = FrameLayout(this)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xE61A1A22.toInt(), 0xF20A0A12.toInt(), 0xE61A1A22.toInt())
        )
        v.background = bg
        v.alpha = 0f
        v.visibility = View.GONE
        shield = v
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
    }

    private fun animateShield(on: Boolean) {
        val v = shield ?: return
        handler.post {
            if (on) {
                v.visibility = View.VISIBLE
                v.animate().alpha(1f).scaleX(1.025f).scaleY(1.025f)
                    .setDuration(260).setInterpolator(DecelerateInterpolator()).start()
            } else {
                v.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(210)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { v.visibility = View.GONE }.start()
            }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                detector.process(image).addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    val left = face?.leftEyeOpenProbability ?: 0f
                    val right = face?.rightEyeOpenProbability ?: 0f
                    val rot = face?.headEulerAngleY ?: 999f
                    val looking = face != null && left > 0.35f && right > 0.35f && abs(rot) < 18f
                    if (!looking) {
                        if (awaySince == 0L) awaySince = SystemClock.uptimeMillis()
                        if (!lastAway && SystemClock.uptimeMillis() - awaySince > 280) {
                            lastAway = true; animateShield(true)
                        }
                    } else {
                        awaySince = 0L
                        if (lastAway) { lastAway = false; animateShield(false) }
                    }
                }.addOnCompleteListener { proxy.close() }
            }
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                object : androidx.lifecycle.LifecycleOwner {
                    private val registry = androidx.lifecycle.LifecycleRegistry(this)
                    init { registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
                    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
                },
                CameraSelector.DEFAULT_FRONT_CAMERA, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("shield", "Look Away Shield", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        shield?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
    override fun onBind(intent: android.content.Intent?) = null
}
