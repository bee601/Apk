package com.example.lookawayshield

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            setBackgroundColor(Color.rgb(16, 19, 26))
        }
        root.addView(TextView(this).apply {
            text = "LOOK AWAY HOME"
            textSize = 12f
            setTextColor(Color.rgb(168, 179, 199))
        })
        root.addView(TextView(this).apply {
            text = "Your apps, one calm space."
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 20)
        })
        root.addView(TextView(this).apply {
            text = "Widgets from your previous launcher cannot be copied automatically. Add them again here when launcher hosting is enabled."
            textSize = 14f
            setTextColor(Color.rgb(168, 179, 199))
            setPadding(0, 0, 0, 20)
        })
        val grid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = true
        }
        val apps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).distinctBy { it.activityInfo.packageName }.sortedBy {
            it.loadLabel(packageManager).toString().lowercase()
        }
        apps.forEach { info -> grid.addView(appButton(info)) }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Shield settings"
            setOnClickListener { startActivity(Intent(this@LauncherActivity, MainActivity::class.java)) }
        })
        setContentView(root)
    }

    private fun appButton(info: ResolveInfo): Button = Button(this).apply {
        text = info.loadLabel(packageManager)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setCompoundDrawablesWithIntrinsicBounds(null, info.loadIcon(packageManager), null, null)
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        setOnClickListener {
            packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)?.let(::startActivity)
        }
    }
}