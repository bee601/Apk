package com.example.lookawayshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val req = 42
    private val imagePicker = 43
    private lateinit var permissionStatus: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var hexInput: EditText
    private lateinit var excludedPackages: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permissionStatus)
        imagePreview = findViewById(R.id.imagePreview)
        hexInput = findViewById(R.id.hexInput)
        excludedPackages = findViewById(R.id.excludedPackages)
        refreshPermissionStatus()
        loadSelectedImage()
        loadSettings()
        findViewById<View>(R.id.content).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.fade_slide_up)
        )

        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.usagePermissionButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.imageButton).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, imagePicker
            )
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                refreshPermissionStatus()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), req)
            } else startShield()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, ShieldService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) refreshPermissionStatus()
    }

    @Deprecated("Deprecated in Android API  Activity Result APIs are not used in this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == imagePicker && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                getPreferences(MODE_PRIVATE).edit().putString("shield_image", uri.toString()).apply()
                showSelectedImage(uri)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == req && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startShield()
    }

    private fun startShield() {
        val intent = Intent(this, ShieldService::class.java)
        saveSettings()
        getPreferences(MODE_PRIVATE).getString("shield_image", null)?.let {
            intent.putExtra(ShieldService.EXTRA_IMAGE_URI, it)
        }
        intent.putExtra(ShieldService.EXTRA_STYLE, selectedStyle())
        intent.putExtra(ShieldService.EXTRA_COLOR, normalizedColor())
        intent.putExtra(ShieldService.EXTRA_EXCLUDED_PACKAGES, excludedPackages.text.toString())
        ContextCompat.startForegroundService(this, intent)
    }

    private fun refreshPermissionStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val usage = getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
        permissionStatus.text = if (overlay && camera) {
            "READY  ·  on-device only  ·  sensitive apps ${if (usage) "paused" else "optional"}"
        } else {
            "SETUP NEEDED  ·  camera ${if (camera) "ready" else "off"}  ·  overlay ${if (overlay) "ready" else "off"}"
        }
    }

    private fun selectedStyle(): String = when (findViewById<RadioGroup>(R.id.styleGroup).checkedRadioButtonId) {
        R.id.styleAurora -> "aurora"
        R.id.stylePaper -> "paper"
        else -> "midnight"
    }

    private fun normalizedColor(): String {
        val value = hexInput.text.toString().trim().removePrefix("#")
        return if (value.matches(Regex("[0-9a-fA-F]{6}"))) "#$value" else "#7C5CFC"
    }

    private fun saveSettings() {
        getPreferences(MODE_PRIVATE).edit()
            .putString("shield_style", selectedStyle())
            .putString("shield_color", normalizedColor())
            .putString("excluded_packages", excludedPackages.text.toString())
            .apply()
    }

    private fun loadSettings() {
        val preferences = getPreferences(MODE_PRIVATE)
        hexInput.setText(preferences.getString("shield_color", "#7C5CFC"))
        excludedPackages.setText(preferences.getString("excluded_packages", ""))
        when (preferences.getString("shield_style", "midnight")) {
            "aurora" -> findViewById<RadioGroup>(R.id.styleGroup).check(R.id.styleAurora)
            "paper" -> findViewById<RadioGroup>(R.id.styleGroup).check(R.id.stylePaper)
            else -> findViewById<RadioGroup>(R.id.styleGroup).check(R.id.styleMidnight)
        }
    }

    private fun loadSelectedImage() {
        getPreferences(MODE_PRIVATE).getString("shield_image", null)?.let {
            runCatching { showSelectedImage(Uri.parse(it)) }
        }
    }

    private fun showSelectedImage(uri: Uri) {
        imagePreview.setImageURI(uri)
        imagePreview.visibility = View.VISIBLE
    }
}
