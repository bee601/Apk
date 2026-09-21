package com.example.lookawayshield

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

class ShieldService : Service() {
    companion object {
        const val EXTRA_IMAGE_URI = "shield_image_uri"
        const val EXTRA_STYLE = "shield_style"
        const val EXTRA_COLOR = "shield_color"
        const val EXTRA_EXCLUDED_PACKAGES = "excluded_packages"
    }
    private lateinit var wm: WindowManager
    private var shield: View? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastAway = false
    private var awaySince = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var style = "midnight"
    private var color = Color.parseColor("#7C5CFC")
    private var imageUri: String? = null
    private var excludedPackages = emptySet<String>()
    private var pausedForSensitiveApp = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(7, NotificationCompat.Builder(this, "shield")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Look Away Shield")
            .setContentText("On-device gaze protection is active")
            .setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        imageUri = intent?.getStringExtra(EXTRA_IMAGE_URI)
        style = intent?.getStringExtra(EXTRA_STYLE) ?: "midnight"
        color = runCatching { Color.parseColor(intent?.getStringExtra(EXTRA_COLOR) ?: "#7C5CFC") }
            .getOrDefault(Color.parseColor("#7C5CFC"))
        excludedPackages = intent?.getStringExtra(EXTRA_EXCLUDED_PACKAGES).orEmpty()
            .split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        addShield(imageUri)
        scheduleSensitiveAppCheck()
        return START_STICKY
    }

    private fun addShield(imageUri: String?) {
        if (shield != null) return
        val v = FrameLayout(this)
        val bg = shieldBackground()
        v.background = bg
        if (imageUri != null) {
            runCatching {
                contentResolver.openInputStream(Uri.parse(imageUri))?.use {
                    ImageView(this).apply {
                        setImageBitmap(BitmapFactory.decodeStream(it))
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.also { image -> v.addView(image, FrameLayout.LayoutParams(-1, -1)) }
                }
            }
        }
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

    private fun shieldBackground(): GradientDrawable {
        val base = when (style) {
            "aurora" -> intArrayOf(0xEE101B2D.toInt(), withAlpha(color, 210), 0xEE102A29.toInt())
            "paper" -> intArrayOf(0xF4F6F1E9.toInt(), withAlpha(color, 185), 0xF4E3E8E4.toInt())
            else -> intArrayOf(0xF2111424.toInt(), withAlpha(color, 220), 0xF20B0D16.toInt())
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, base)
    }

    private fun withAlpha(value: Int, alpha: Int): Int = (value and 0x00FFFFFF) or (alpha shl 24)

    private fun scheduleSensitiveAppCheck() {
        handler.post(object : Runnable {
            override fun run() {
                val shouldPause = isSensitiveAppInForeground()
                if (shouldPause != pausedForSensitiveApp) {
                    pausedForSensitiveApp = shouldPause
                    if (shouldPause) animateShield(false) else if (lastAway) animateShield(true)
                }
                handler.postDelayed(this, 700)
            }
        })
    }

    private fun isSensitiveAppInForeground(): Boolean {
        val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(System.currentTimeMillis() - 3000, System.currentTimeMillis())
        val event = UsageEvents.Event()
        var currentPackage = ""
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) currentPackage = event.packageName
        }
        if (currentPackage == packageName) return false
        val blocked = setOf("sparkasse", "banking", "authenticator", "authentication", "bankid", "com.google.android.apps.authenticator2")
        return (blocked + excludedPackages).any { token -> currentPackage.lowercase().contains(token) }
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
                    if (!looking && !pausedForSensitiveApp) {
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
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        shield?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
    override fun onBind(intent: android.content.Intent?) = null
}
