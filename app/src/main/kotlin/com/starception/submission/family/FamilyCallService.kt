package com.starception.submission.family

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.starception.submission.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class FamilyMessagingService : FirebaseMessagingService() {
    @Inject lateinit var client: FamilyClient
    override fun onNewToken(token: String) { FamilyTokenWorker.schedule(this) }
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["v"] != "1" || message.priority != RemoteMessage.PRIORITY_HIGH) return
        val id = data["callId"] ?: return
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) return
        val expires = data["expires"]?.toLongOrNull() ?: return
        if (expires <= System.currentTimeMillis() || expires > System.currentTimeMillis() + 60000) return
        if (client.credentials.load()?.first != data["callee"]) return
        if (client.state.value.call?.let { it.id == id && it.stage != "ringing" } == true) return
        runCatching { ContextCompat.startForegroundService(this,
            Intent(this, FamilyCallService::class.java).putExtra("callId", id)) }
    }
}

@AndroidEntryPoint
class FamilyCallService : Service() {
    @Inject lateinit var client: FamilyClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: Job? = null
    private var resuming: Job? = null
    private var ringtone: android.media.Ringtone? = null
    private var foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeWake = false
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Family calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming and ongoing voice and video calls"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
            })
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, "Ongoing Family calls", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        FamilyTelecom.register(this)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "Deenly:FamilyCall").apply { setReferenceCounted(false); acquire(60000) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i("FamilyCalls", "Call service start: hasCall=${client.state.value.call != null}; resumeRequested=${intent?.hasExtra("callId") == true}")
        if (intent?.action == DECLINE) {
            if (intent.getStringExtra("callId") == client.state.value.call?.id) client.hangup("notification-decline")
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 30 && client.hasForegroundActivity) {
            if (intent?.getBooleanExtra("microphone", false) == true) foregroundTypes = foregroundTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (intent?.getBooleanExtra("camera", false) == true) foregroundTypes = foregroundTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        foreground(notification(client.state.value))
        intent?.getStringExtra("callId")?.let { id ->
            if (resuming?.isActive != true) resuming = scope.launch {
                val valid = withTimeoutOrNull(25000) { client.resumeForPush(id) } == true
                if (!valid && client.state.value.call == null) { FamilyTelecom.end(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        }
        if (observing == null) observing = scope.launch {
            client.state.collect { state ->
                if (state.call != null) {
                    if (state.call.stage == "active" && !activeWake) {
                        activeWake = true; wakeLock?.acquire(56 * 60 * 1000L)
                    }
                    val incoming = state.call.stage == "ringing" && state.call.callee == state.me
                    if (incoming && ringtone == null) runCatching {
                        ringtone = android.media.RingtoneManager.getRingtone(this@FamilyCallService,
                            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))?.apply {
                            isLooping = true; play()
                        }
                    }
                    if (!incoming) { ringtone?.stop(); ringtone = null }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                    FamilyTelecom.update(this@FamilyCallService, state)
                } else if (resuming?.isActive != true) {
                    FamilyTelecom.end(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun foreground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, foregroundTypes)
        else startForeground(NOTIFICATION_ID, notification)
    }
    private fun notification(state: FamilyUiState): Notification {
        val call = state.call
        val incoming = call?.stage == "ringing" && call.callee == state.me
        fun activity(code: Int, answer: Boolean) = PendingIntent.getActivity(this, code,
            Intent(this, FamilyIncomingActivity::class.java).putExtra("callId", call?.id).putExtra("answer", answer)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val view = activity(10, false)
        val decline = PendingIntent.getService(this, 12, Intent(this, FamilyCallService::class.java).setAction(DECLINE).putExtra("callId", call?.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, if (incoming) CHANNEL else ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(call?.peer(state.me)?.substringBefore('@') ?: "Family call")
            .setContentText(if (incoming) "Incoming ${call?.kind} call" else if (call == null) "Checking incoming call…" else if (call.stage == "ringing") "Calling…" else if (!state.mediaConnected) "Connecting…" else "Family call in progress")
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(if (incoming) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(view)
        if (call != null) {
            val person = androidx.core.app.Person.Builder().setName(call.peer(state.me).substringBefore('@'))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setImportant(true).build()
            builder.setStyle(if (incoming) NotificationCompat.CallStyle.forIncomingCall(person, decline, activity(11, true))
                else NotificationCompat.CallStyle.forOngoingCall(person, decline))
            if (incoming) builder.setFullScreenIntent(view, true).setTimeoutAfter((call.expires - System.currentTimeMillis()).coerceAtLeast(1))
        }
        return builder.build()
    }
    override fun onDestroy() {
        android.util.Log.i("FamilyCalls", "Call service destroyed: hasCall=${client.state.value.call != null}")
        ringtone?.stop(); if (wakeLock?.isHeld == true) wakeLock?.release()
        scope.cancel(); FamilyTelecom.end("service-destroyed"); super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "family_calls"
        private const val ONGOING_CHANNEL = "family_calls_ongoing"
        private const val NOTIFICATION_ID = 7801
        private const val DECLINE = "family.decline"
        fun ensure(context: Context, microphone: Boolean = false, camera: Boolean = false) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FamilyCallService::class.java)
                .putExtra("microphone", microphone).putExtra("camera", camera)) }
        }
    }
}
