package com.hollandhaptics.babyapp

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = REQUIRED_PERMISSIONS.all { results[it] == true }
        if (allGranted) {
            startBabyService()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)

        // On Android 15+ (targetSdk 35) edge-to-edge is on by default, so the status
        // and navigation bars draw under the activity. Add the system bar insets to
        // the root padding so children stay clear of them.
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

        findViewById<Button>(R.id.start).setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.battery).setOnClickListener { openBatteryOptimizationSettings() }

        // Auto-start if launched from the boot-resume notification, or always-on intent.
        if (intent?.action == ACTION_RESUME) onStartClicked()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_RESUME) onStartClicked()
    }

    private fun onStartClicked() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions.launch(missing.toTypedArray())
            return
        }
        startBabyService()
        refreshStatus()
    }

    private fun startBabyService() {
        val intent = Intent(this, BabyService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun refreshStatus() {
        status.setText(if (isBabyServiceRunning()) R.string.service_running else R.string.service_stopped)
    }

    @Suppress("DEPRECATION")
    private fun isBabyServiceRunning(): Boolean {
        // getRunningServices() only returns this app's own services on modern Android,
        // which is exactly what we need.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = BabyService::class.java.name
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == name }
    }

    private fun openBatteryOptimizationSettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already exempt", Toast.LENGTH_SHORT).show()
            return
        }
        // Open the system list and let the user opt this app out of optimizations.
        // We deliberately do not request the auto-grant permission, which Play Store
        // rejects for general-purpose use.
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    companion object {
        const val ACTION_RESUME = "com.hollandhaptics.babyapp.action.RESUME_LISTENING"

        private val REQUIRED_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}
