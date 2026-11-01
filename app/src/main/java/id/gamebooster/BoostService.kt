package id.gamebooster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BoostService : Service() {
    companion object {
        const val ACTION_STOP = "id.gamebooster.STOP"
        private const val CH = "gb_monitor"
        private const val CH_ALERT = "gb_alert"
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, BoostService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private var overlay: Overlay? = null
    private var overheated = false
    private var miss = 0
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Monitor Game Booster", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Peringatan suhu", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        stopping = false
        ServiceCompat.startForeground(
            this, 1, notif("Memantau…"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        StateHub.monitoring.value = true
        if (job == null) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun shutdown() {
        stopping = true
        job?.cancel()
        job = null
        Thread {
            if (Booster.isActive()) Booster.disable()
            main.post {
                overlay?.remove()
                overlay = null
                StateHub.monitoring.value = false
                StateHub.fps.value = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private suspend fun loop() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            try {
                step(tick++)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            delay(1000)
        }
    }

    private fun step(tick: Int) {
        val s = Monitor.read()
        StateHub.stats.value = s
        val auto = Prefs.bool("auto")
        val ov = Prefs.bool("overlay")
        val games = Prefs.games
        val owned = Booster.isActive() && Prefs.bool("auto_owned")
        val fg = if (auto || ov || owned) Monitor.foreground() else null
        StateHub.fg.value = fg

        if (tick % 4 == 3 && Booster.syncState()) alert("Boost dihentikan oleh watchdog suhu")

        // Batas suhu aman
        val bl = Prefs.int("batt_limit", 44)
        val cl = Prefs.int("cpu_limit", 90)
        if (s != null && Booster.isActive() && (s.battTemp >= bl || s.cpuTemp >= cl)) {
            Booster.disable()
            overheated = true
            alert("Boost dihentikan: baterai ${"%.1f".format(s.battTemp)}°C, CPU ${"%.0f".format(s.cpuTemp)}°C")
        }
        if (overheated && s != null && s.battTemp < bl - 3 && s.cpuTemp < cl - 8) overheated = false

        // Auto-boost
        if (auto && !overheated && !Booster.isActive() && fg != null && fg in games) {
            Booster.enable(fg, owned = true)
            miss = 0
        } else if (Booster.isActive() && Prefs.bool("auto_owned") && fg != null && fg != packageName) {
            if (fg == Prefs.str("gpkg") || fg in games) miss = 0
            else if (++miss >= 4) {
                Booster.disable()
                miss = 0
            }
        }

        // FPS
        val target = fg?.takeIf { it != packageName && (ov || it in games) }
        val f = target?.let { FpsMeter.sample(it) }
        StateHub.fps.value = f

        // Overlay
        val text = buildString {
            append("FPS ${f?.let { "%.0f".format(it) } ?: "--"}")
            if (s != null) {
                append("\nCPU ${s.cpuLoad}% ${"%.0f".format(s.cpuTemp)}°  GPU ${s.gpuLoad}%")
                append("\nBAT ${"%.1f".format(s.battTemp)}°  RAM ${s.ramUsedMb}M")
            }
        }
        main.post {
            if (stopping) return@post
            if (ov) {
                if (overlay == null) overlay = Overlay(this)
                overlay?.show()
                overlay?.update(text)
            } else {
                overlay?.remove()
            }
        }

        if (tick % 3 == 0) {
            val state = if (Booster.isActive()) "Boost ON" else "Standby"
            val extra = s?.let { " • CPU ${"%.0f".format(it.cpuTemp)}° • BAT ${"%.1f".format(it.battTemp)}°" } ?: ""
            getSystemService(NotificationManager::class.java).notify(1, notif(state + extra))
        }
    }

    private fun notif(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BoostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Game Booster")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun alert(text: String) {
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Game Booster")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }
}
