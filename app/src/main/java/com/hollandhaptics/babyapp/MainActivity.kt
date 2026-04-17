package com.hollandhaptics.babyapp

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var indicator: View
    private lateinit var status: TextView
    private lateinit var toggle: MaterialButton
    private lateinit var battery: MaterialButton

    private val uiHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            uiHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = REQUIRED_PERMISSIONS.all { results[it] == true }
        if (allGranted) {
            startBabyService()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        indicator = findViewById(R.id.indicator)
        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        battery = findViewById(R.id.battery)

        // Edge-to-edge inset handling: add system bar insets to the existing root padding
        // (don't use fitsSystemWindows, which would replace it).
        val root = findViewById<View>(R.id.root)
        val basePadding = Rect(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = basePadding.left + bars.left,
                top = basePadding.top + bars.top,
                right = basePadding.right + bars.right,
                bottom = basePadding.bottom + bars.bottom,
            )
            insets
        }

        toggle.setOnClickListener { onToggleClicked() }
        battery.setOnClickListener { openBatteryOptimizationSettings() }

        if (intent?.action == ACTION_RESUME) onToggleClicked()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(refreshRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_RESUME && !isBabyServiceRunning()) onToggleClicked()
    }

    private fun onToggleClicked() {
        if (isBabyServiceRunning()) {
            stopBabyService()
            // Give the framework a moment to tear the service down, then refresh.
            uiHandler.postDelayed({ refresh() }, 200)
            return
        }
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions.launch(missing.toTypedArray())
            return
        }
        startBabyService()
        uiHandler.postDelayed({ refresh() }, 200)
    }

    private fun startBabyService() {
        ContextCompat.startForegroundService(this, Intent(this, BabyService::class.java))
    }

    private fun stopBabyService() {
        startService(Intent(this, BabyService::class.java).setAction(BabyService.ACTION_STOP))
    }

    private fun refresh() {
        val running = isBabyServiceRunning()
        val state = if (running) BabyService.state else BabyService.Companion.State.STOPPED

        // Indicator color + label
        val (colorRes, labelRes) = when (state) {
            BabyService.Companion.State.STOPPED -> R.color.indicator_stopped to R.string.state_stopped
            BabyService.Companion.State.LISTENING -> R.color.indicator_listening to R.string.state_listening
            BabyService.Companion.State.RECORDING -> R.color.indicator_recording to R.string.state_recording
        }
        indicator.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        status.setText(labelRes)

        // Toggle button
        toggle.setText(if (running) R.string.btn_stop else R.string.btn_start)

        // Battery button
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        battery.isEnabled = !exempt
        battery.setText(if (exempt) R.string.btn_battery_done else R.string.btn_battery)
    }

    @Suppress("DEPRECATION")
    private fun isBabyServiceRunning(): Boolean {
        // getRunningServices() returns this app's own services on modern Android.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = BabyService::class.java.name
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == name }
    }

    private fun openBatteryOptimizationSettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, R.string.btn_battery_done, Toast.LENGTH_SHORT).show()
            return
        }
        // Open the system list and let the user opt this app out of optimizations.
        // We deliberately do not request the auto-grant permission, which Play Store
        // rejects for general-purpose use.
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    companion object {
        const val ACTION_RESUME = "com.hollandhaptics.babyapp.action.RESUME_LISTENING"
        private const val REFRESH_INTERVAL_MS = 500L

        private val REQUIRED_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}
