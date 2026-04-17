package com.hollandhaptics.babyapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Foreground service that monitors the microphone and uploads recordings
 * whose peak amplitude exceeds [AMPLITUDE_THRESHOLD].
 *
 * Always-running mechanism, end to end:
 *  1. Started as a microphone-typed foreground service from MainActivity (visible activity).
 *  2. While running, the OS treats it as user-essential and will not kill it
 *     except under extreme memory pressure.
 *  3. Returns START_STICKY so the OS restarts it if it ever is killed.
 *  4. After reboot, BootReceiver posts a notification; tapping it launches
 *     MainActivity, which restarts this service. (Mic FGS cannot be started
 *     directly from BOOT_COMPLETED on Android 14+; notification interaction
 *     is one of the documented while-in-use exemptions.)
 */
class BabyService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val gate = AmplitudeGate(AMPLITUDE_THRESHOLD, SILENCE_TICKS_BEFORE_STOP)

    private lateinit var recordingsDir: File
    private lateinit var deviceId: String
    private lateinit var uploader: Uploader

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        recordingsDir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        deviceId = readDeviceId()
        uploader = Uploader(getString(R.string.file_upload_url))
        state = State.LISTENING
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun readDeviceId(): String =
        // ANDROID_ID is stable per app-signing-key + per device; rotates on factory reset.
        // Used here to namespace recording filenames so multiple phones don't collide on the server.
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (recorder == null) {
            startNewRecording()
            mainHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mainHandler.removeCallbacks(tickRunnable)
        stopAndReleaseRecorder(keepFile = false)
        ioExecutor.shutdown()
        state = State.STOPPED
        super.onDestroy()
    }

    private fun tick() {
        val r = recorder ?: return
        val amplitude = r.maxAmplitude
        when (gate.onSample(amplitude)) {
            AmplitudeGate.Decision.CONTINUE -> Unit
            AmplitudeGate.Decision.STOP_AND_KEEP -> {
                stopAndReleaseRecorder(keepFile = true)
                triggerUploadAll()
                startNewRecording()
            }
            AmplitudeGate.Decision.STOP_AND_DISCARD -> {
                stopAndReleaseRecorder(keepFile = false)
                startNewRecording()
            }
        }
        state = if (gate.hasRecorded) State.RECORDING else State.LISTENING
    }

    private fun startNewRecording() {
        val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
        val file = File(recordingsDir, "${deviceId}_$timestamp.3gpp")
        val r = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
        }
        try {
            r.prepare()
            r.start()
            recorder = r
            currentFile = file
            gate.reset()
            Log.d(TAG, "recording -> ${file.name}")
        } catch (e: IOException) {
            Log.e(TAG, "prepare/start failed", e)
            r.release()
            recorder = null
            currentFile = null
        }
    }

    private fun stopAndReleaseRecorder(keepFile: Boolean) {
        val r = recorder ?: return
        recorder = null
        try {
            r.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "recorder.stop() failed", t)
        }
        r.release()
        if (!keepFile) currentFile?.delete()
        currentFile = null
    }

    private fun triggerUploadAll() {
        val files = recordingsDir.listFiles()?.toList().orEmpty()
        if (files.isEmpty()) return
        ioExecutor.execute {
            for (f in files) {
                if (uploader.upload(f) && !f.delete()) {
                    Log.w(TAG, "could not delete ${f.name} after upload")
                }
            }
        }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()

    private fun startForegroundCompat() {
        ensureNotificationChannel()
        val tapIntent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, BabyService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(tap)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // FOREGROUND_SERVICE_TYPE_MICROPHONE constant added in API 30.
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableLights(true)
            lightColor = 0xFF0000FF.toInt()
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "BabyService"
        const val CHANNEL_ID = "baby_app_listening"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.hollandhaptics.babyapp.action.STOP"
        private const val TICK_INTERVAL_MS = 1000L
        private const val AMPLITUDE_THRESHOLD = 1000
        private const val SILENCE_TICKS_BEFORE_STOP = 60

        enum class State { STOPPED, LISTENING, RECORDING }

        /** Volatile snapshot of the service's current state. Written only by BabyService;
         *  read by MainActivity. */
        @Volatile
        var state: State = State.STOPPED
    }
}
