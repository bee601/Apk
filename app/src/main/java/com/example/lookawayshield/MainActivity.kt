package com.example.lookawayshield

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val accent = Color.rgb(255, 176, 240)
    private val habits = mutableListOf("Morning walk", "Read 20 pages", "Drink 2L water")
    private val completed = mutableSetOf<String>()
    private lateinit var habitList: LinearLayout
    private lateinit var progressLabel: TextView
    private lateinit var streakLabel: TextView
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getPreferences(MODE_PRIVATE)
        habits.addAll(prefs.getStringSet("habits", emptySet()).orEmpty().filter { it !in habits })
        restoreToday()
        habitList = findViewById(R.id.habitList)
        progressLabel = findViewById(R.id.progressLabel)
        streakLabel = findViewById(R.id.streakLabel)
        findViewById<TextView>(R.id.dateLabel).text = SimpleDateFormat("EEEE, d MMMM", Locale.US)
            .format(Date()).uppercase(Locale.US)
        findViewById<View>(R.id.content).startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_slide_up))
        findViewById<View>(R.id.addHabit).setOnClickListener { showAddHabitDialog() }
        renderHabits()
    }

    private fun renderHabits() {
        habitList.removeAllViews()
        habits.forEachIndexed { index, habit ->
            val row = CheckBox(this).apply {
                text = habit
                textSize = 16f
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                setPadding(12, 4, 12, 4)
                isChecked = habit in completed
                setOnCheckedChangeListener { _, checked ->
                    if (checked) completed.add(habit) else completed.remove(habit)
                    saveToday(); updateSummary()
                }
            }
            val card = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.habit_card)
                setPadding(8, 8, 8, 8)
                addView(row, LinearLayout.LayoutParams(-1, 64))
                alpha = 0f
                translationY = 18f
                animate().alpha(1f).translationY(0f).setStartDelay(index * 70L).setDuration(360).start()
            }
            habitList.addView(card, LinearLayout.LayoutParams(-1, 76).apply { bottomMargin = 10 })
        }
        updateSummary()
    }

    private fun updateSummary() {
        val done = completed.size
        progressLabel.text = "$done / ${habits.size} complete"
        streakLabel.text = "${prefs.getInt("streak", 4)} day streak"
        findViewById<View>(R.id.progressBar).setBackgroundColor(accent)
        findViewById<View>(R.id.progressBar).layoutParams.width =
            (resources.displayMetrics.widthPixels - 48) * done.coerceAtMost(habits.size) / habits.size.coerceAtLeast(1)
        findViewById<View>(R.id.progressBar).requestLayout()
    }

    private fun showAddHabitDialog() {
        val input = EditText(this).apply { hint = "e.g. Stretch for 5 minutes"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("New habit").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    habits.add(it); prefs.edit().putStringSet("habits", habits.toSet()).apply(); renderHabits()
                }
            }.show()
    }

    private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun restoreToday() {
        completed.addAll(prefs.getStringSet("done_${todayKey()}", emptySet()).orEmpty())
    }

    private fun saveToday() {
        prefs.edit().putStringSet("done_${todayKey()}", completed).putInt("streak", 4 + if (completed.isNotEmpty()) 1 else 0).apply()
    }
}
