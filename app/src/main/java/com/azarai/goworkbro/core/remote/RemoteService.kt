package com.azarai.goworkbro.core.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.MainActivity
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Live state of the link, for the settings page. Process-wide, dies with it. */
object RemoteStatus {
    val running = MutableStateFlow(false)
    val clients = MutableStateFlow(0)
    /** Why the last attempt failed; cleared once shown (never persisted). */
    val lastError = MutableStateFlow<String?>(null)
}

/**
 * Keeps the Bluetooth link alive while the app is not on screen — that is the
 * whole point of 电脑协同, and a live GATT server is not something Android
 * keeps running for a backgrounded app.
 *
 * Type `connectedDevice` on purpose: this service exists to serve a paired
 * computer over Bluetooth, which is exactly what the platform's foreground
 * service rules describe.
 */
class RemoteService : Service() {

    companion object {
        private const val CHANNEL_ID = "remote"
        private const val NOTIFICATION_ID = 41
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RemoteService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteService::class.java))
        }

        /**
         * Brings the link back after a process death (MIUI is fond of those),
         * but only if the user left 电脑协同 on and the radio is still usable.
         */
        fun ensureRunning(context: Context) {
            if (Graph.testing) return // Robolectric has no radio, and the DB is per-test
            if (RemoteStatus.running.value) return
            scope.launch {
                if (Graph.store.get(Store.Keys.REMOTE_ENABLED) != "true") return@launch
                if (RemoteBle.readiness(context) !is RemoteBle.Readiness.Ready) return@launch
                start(context)
            }
        }
    }

    private var ble: BleServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (RemoteBle.readiness(this) !is RemoteBle.Readiness.Ready) {
            // The switch can outlive the permission (a revoke, a Bluetooth
            // toggle) — go quiet instead of crashing on a SecurityException.
            RemoteStatus.running.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (ble == null) {
            val server = BleServer(
                context = this,
                onRequest = { request -> RemoteApi.handle(Graph.db, request) },
                onClients = { count -> RemoteStatus.clients.value = count },
            )
            val error = server.start()
            if (error != null) {
                RemoteStatus.lastError.value = error
                RemoteStatus.running.value = false
                stopSelf()
                return START_NOT_STICKY
            }
            ble = server
        }
        RemoteStatus.running.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ble?.stop()
        ble = null
        RemoteStatus.running.value = false
        RemoteStatus.clients.value = 0
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "电脑协同", NotificationManager.IMPORTANCE_LOW).apply {
                description = "电脑通过蓝牙读写待办时显示的常驻通知"
                setShowBadge(false)
            },
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle("电脑协同中")
            .setContentText("电脑可以读写待办、习惯、倒计时")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
}
