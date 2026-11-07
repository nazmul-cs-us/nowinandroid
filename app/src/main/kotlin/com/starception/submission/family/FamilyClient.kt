package com.starception.submission.family

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smack.provider.IQProvider
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.xml.XmlPullParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

internal val FAMILY_DOMAIN = FamilyEndpoint().domain
internal const val FAMILY_NS = "urn:starception:family:1"

data class FamilyWebPairing(val code: String, val url: String, val expires: Long)
data class FamilyMember(val jid: String, val status: String, val presence: String = "unknown")
data class FamilyCall(
    val id: String, val caller: String, val callee: String, val kind: String,
    val stage: String, val version: Long, val expires: Long, val selected: Boolean,
) { fun peer(me: String) = if (caller == me) callee else caller }
data class FamilyUiState(
    val me: String = "", val connected: Boolean = false, val busy: Boolean = false,
    val serviceReady: Boolean = false,
    val webPairing: FamilyWebPairing? = null,
    val callError: String? = null, val pushRegistrationError: String? = null,
    val members: List<FamilyMember> = emptyList(), val call: FamilyCall? = null,
    val error: String? = null, val muted: Boolean = false, val camera: Boolean = true,
    val speaker: Boolean = false, val mediaConnected: Boolean = false,
    val connectedAtElapsed: Long? = null,
)

private class FamilyIq(val body: String) : IQ("family", FAMILY_NS) {
    init { type = Type.set }
    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        xml.rightAngleBracket(); xml.escape(body); return xml
    }
}
private class FamilyIqProvider : IQProvider<FamilyIq>() {
    override fun parse(parser: XmlPullParser, initialDepth: Int, xmlEnvironment: XmlEnvironment): FamilyIq =
        FamilyIq(parser.nextText())
}

/** One process-level socket and media owner; screens never own the connection. */
@Singleton
class FamilyClient @Inject constructor(@ApplicationContext private val context: Context) {
    internal val credentials = FamilyCredentials(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(FamilyUiState())
    val state = mutable.asStateFlow()
    private var connection: XMPPTCPConnection? = null
    private var generation = 0
    private var rtcCallId: String? = null
    private val callReducer = FamilyCallReducer()
    private val presenceCache = FamilyPresenceCache()
    private var foreground = false
    private var activityVersion = 0L
    private val terminal = LinkedHashSet<String>()
    private val queuedSignals = mutableListOf<Pair<String, JSONObject>>()
    private var ringingJob: Job? = null
    private var heartbeat: Job? = null
    private val visibleActivities = mutableSetOf<android.app.Activity>()
    internal val hasForegroundActivity: Boolean get() = visibleActivities.isNotEmpty()
    init {
        android.util.Log.w("FamilyCalls", "Diagnostics v2 loaded; startup tracing enabled; package=${context.packageName}")
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) { activityChanged(true) }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) { activityChanged(false) }
        })
        (context.applicationContext as android.app.Application).registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: android.app.Activity) { visibleActivities.add(activity) }
                override fun onActivityStopped(activity: android.app.Activity) {
                    // An activity (notably the splash) may have started before this
                    // singleton existed. Its stop must not hide the new activity.
                    visibleActivities.remove(activity)
                    // Telecom and the call foreground service now own background calls.
                }
                override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) = Unit
                override fun onActivityResumed(a: android.app.Activity) = Unit
                override fun onActivityPaused(a: android.app.Activity) = Unit
                override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) = Unit
                override fun onActivityDestroyed(a: android.app.Activity) { visibleActivities.remove(a) }
            })
    }
    private fun activityChanged(active: Boolean) {
        if (foreground == active) return
        foreground = active; activityVersion++
        if (mutable.value.connected) scope.launch { runCatching { request("presence") } }
    }
    private var mediaInstance: AndroidFamilyMedia? = null
    val media: AndroidFamilyMedia get() = mediaInstance ?: createMedia().also { mediaInstance = it }
    private fun createMedia() = AndroidFamilyMedia(context, scope,
        signal = { payload -> mutable.value.call?.let { call ->
            scope.launch { runCatching { request("signal", "callId" to call.id, "payload" to payload) }
                .onFailure { endWithError("Call signalling was interrupted.") } }
        } },
        connected = { connected -> mutable.value = mutable.value.copy(mediaConnected = connected,
            connectedAtElapsed = mutable.value.connectedAtElapsed ?: if (connected) android.os.SystemClock.elapsedRealtime() else null) },
        failed = { message -> scope.launch { endWithError(message) } },
    )

    fun signUp(username: String, password: String, confirmation: String) = scope.launch {
        if (mutable.value.busy || mutable.value.connected) return@launch
        val policy = FamilyRegistrationPolicy()
        policy.validate(username, password, confirmation)?.let {
            mutable.value = mutable.value.copy(error = policy.message(it)); return@launch
        }
        val name = policy.username(username) ?: return@launch
        disconnect(clearSaved = false)
        val token = generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                val socket = XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(FAMILY_DOMAIN).setHost(FAMILY_DOMAIN).setPort(FamilyEndpoint().port)
                    .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.required)
                    .setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()).build())
                try {
                    socket.replyTimeout = 15000
                    socket.connect()
                    check(socket.isSecureConnection) { "TLS required" }
                    org.jivesoftware.smackx.iqregister.AccountManager.getInstance(socket)
                        .createAccount(org.jxmpp.jid.parts.Localpart.from(name), password)
                } finally { runCatching { socket.disconnect() } }
            }
            if (token != generation) return@launch
            mutable.value = mutable.value.copy(busy = false)
            login("$name@$FAMILY_DOMAIN", password).join()
            if (generation == token + 1 && !mutable.value.connected) {
                mutable.value = mutable.value.copy(error = "Account created. Sign in with your new username and password. " + (mutable.value.error ?: ""))
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("FamilyConnection", "Signup failed: ${e.javaClass.simpleName}")
            if (token == generation) {
                val code = (e as? org.jivesoftware.smack.XMPPException.XMPPErrorException)?.stanzaError?.condition?.toString() ?: "unknown"
                mutable.value = mutable.value.copy(busy = false, error = policy.message(code))
            }
        }
    }

    fun login(jidInput: String, password: String) = scope.launch {
        if (mutable.value.busy) return@launch
        val jid = FamilyEndpoint().account(jidInput)
        if (jid == null || !jid.endsWith("@$FAMILY_DOMAIN") || password.isBlank()) {
            mutable.value = mutable.value.copy(error = "Enter your family account and password."); return@launch
        }
        disconnect(clearSaved = false)
        val token = generation
        mutable.value = FamilyUiState(me = jid, busy = true)
        var attemptedSocket: XMPPTCPConnection? = null
        var signInPhase = "initialization"
        try {
            ProviderManager.addIQProvider("family", FAMILY_NS, FamilyIqProvider())
            val socket = withContext(Dispatchers.IO) {
                XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(FAMILY_DOMAIN).setHost(FAMILY_DOMAIN).setPort(FamilyEndpoint().port)
                    .setUsernameAndPassword(jid.substringBefore('@'), password)
                    .setResource("android-${credentials.deviceId}-${UUID.randomUUID().toString().take(8)}")
                    .setSecurityMode(org.jivesoftware.smack.ConnectionConfiguration.SecurityMode.required)
                    .setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
                    .build()).also { socket ->
                    attemptedSocket = socket
                    socket.replyTimeout = 15000
                    socket.addAsyncStanzaListener({ stanza ->
                        val message = stanza as? Message
                        if (message?.from?.toString() == FAMILY_DOMAIN) {
                            runCatching { JSONObject(message.body ?: "") }.getOrNull()?.let { body ->
                                scope.launch { if (token == generation) event(body) }
                            }
                        }
                    }, org.jivesoftware.smack.filter.StanzaTypeFilter.MESSAGE)
                    socket.addConnectionListener(object : ConnectionListener {
                        override fun connectionClosed() {
                            android.util.Log.w("FamilyCalls", "XMPP connection closed normally")
                            scope.launch { if (mutable.value.connected) lost(token) }
                        }
                        override fun connectionClosedOnError(e: Exception) {
                            android.util.Log.w("FamilyCalls", "XMPP connection closed: ${e.javaClass.simpleName}")
                            scope.launch { if (mutable.value.connected) lost(token) }
                        }
                    })
                    signInPhase = "connect"
                    socket.connect()
                    signInPhase = "authenticate"
                    socket.login()
                }
            }
            if (token != generation) { withContext(Dispatchers.IO) { socket.disconnect() }; return@launch }
            signInPhase = "local-session"
            connection = socket
            mutable.value = mutable.value.copy(connected = true, busy = false)
            runCatching { credentials.save(jid, password) }.onFailure {
                mutable.value = mutable.value.copy(error = "Could not securely save sign-in. Background calling is unavailable.")
            }
            registerPushToken()
            refresh()
            heartbeat = scope.launch {
                while (isActive) { delay(20000); runCatching { apply(request("snapshot")) } }
            }
        } catch (e: Exception) {
            // Never log exception messages/stanzas: they can contain account or authentication data.
            val types = generateSequence<Throwable>(e) { it.cause }.take(8).joinToString(" > ") { it.javaClass.simpleName }
            android.util.Log.w("FamilyConnection", "Sign-in failed at $signInPhase: $types")
            if (token == generation) {
                generation++
                mutable.value = mutable.value.copy(busy = false, connected = false,
                    serviceReady = false, error = signInError(e))
            }
            withContext(Dispatchers.IO) { runCatching { attemptedSocket?.disconnect() } }
            if (e is CancellationException) throw e
        }
    }

    fun disconnect(clearSaved: Boolean = true) {
        android.util.Log.i("FamilyCalls", "Session reset: clearSaved=$clearSaved; hadCall=${mutable.value.call != null}")
        if (clearSaved) credentials.clear()
        generation++
        presenceCache.clear()
        heartbeat?.cancel(); heartbeat = null
        val oldCall = mutable.value.call
        val old = connection
        if (old != null) scope.launch(Dispatchers.IO) {
            if (clearSaved) runCatching {
                val iq = FamilyIq(JSONObject().put("v", 1).put("action", "unregister_device")
                    .put("deviceId", credentials.deviceId).toString())
                iq.to = org.jxmpp.jid.impl.JidCreate.domainBareFrom(FAMILY_DOMAIN)
                old.createStanzaCollectorAndSend(iq).nextResultOrThrow<FamilyIq>()
            }
            if (oldCall != null) runCatching {
                val iq = FamilyIq(JSONObject().put("v", 1).put("action", "end_call").put("callId", oldCall.id).toString())
                iq.to = org.jxmpp.jid.impl.JidCreate.domainBareFrom(FAMILY_DOMAIN)
                old.createStanzaCollectorAndSend(iq).nextResultOrThrow<FamilyIq>()
            }
            runCatching { old.disconnect() }
        }
        connection = null
        ringingJob?.cancel(); mediaInstance?.close(); rtcCallId = null; queuedSignals.clear(); terminal.clear(); callReducer.clear()
        mutable.value = FamilyUiState()
    }
    private fun lost(token: Int) {
        if (token != generation) return
        android.util.Log.w("FamilyCalls", "Session lost: hadCall=${mutable.value.call != null}")
        generation++
        presenceCache.clear()
        heartbeat?.cancel(); heartbeat = null
        mediaInstance?.close(); rtcCallId = null; queuedSignals.clear(); ringingJob?.cancel()
        mutable.value = mutable.value.copy(connected = false, serviceReady = false, busy = false, call = null,
            connectedAtElapsed = null, webPairing = null,
            error = "Connection lost. Sign in again before calling.")
    }
    fun dismissError() { mutable.value = mutable.value.copy(error = null) }
    fun registerPushToken() {
        val session = generation
        fun unavailable() {
            if (session == generation && mutable.value.connected) {
                mutable.value = mutable.value.copy(pushRegistrationError =
                    "Background call registration is unavailable. Calls can still work while both apps are open.")
            }
        }
        // Firebase is optional. A missing/default-app configuration must never undo XMPP authentication.
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (session == generation && mutable.value.connected) scope.launch {
                        try {
                            registerDevice(token)
                            if (session == generation) mutable.value = mutable.value.copy(pushRegistrationError = null)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { unavailable() }
                    }
                }
                .addOnFailureListener { scope.launch { unavailable() } }
        } catch (error: Exception) {
            android.util.Log.w("FamilyConnection", "Optional push setup unavailable: ${error.javaClass.simpleName}")
            unavailable()
        }
    }
    internal suspend fun registerDevice(token: String) {
        request("register_device", "deviceId" to credentials.deviceId, "platform" to "android",
            "token" to token, "environment" to "production")
    }
    internal suspend fun ensureSession(): Boolean {
        if (!mutable.value.connected) {
            val saved = credentials.load() ?: return false
            if (mutable.value.busy) {
                withTimeoutOrNull(16000) { state.first { !it.busy } }
            } else login(saved.first, saved.second).join()
        }
        return mutable.value.connected
    }
    internal suspend fun resumeForPush(callId: String): Boolean {
        if (!ensureSession()) return false
        return runCatching {
            apply(request("snapshot"))
            mutable.value.call?.let { it.id.equals(callId, ignoreCase = true) && it.callee == mutable.value.me && it.stage == "ringing" } == true
        }.getOrDefault(false)
    }
    fun requestWebHelper() = perform {
        val value = request("pair_web_helper", "deviceId" to credentials.deviceId).getJSONObject("webPairing")
        val uri = android.net.Uri.parse(value.getString("url"))
        check(uri.scheme == "https" && uri.host == FAMILY_DOMAIN && uri.path?.trimEnd('/') == "/family-helper")
        mutable.value = mutable.value.copy(webPairing = FamilyWebPairing(value.getString("code"), uri.toString(), value.getLong("expires")))
    }
    fun dismissWebHelper() { mutable.value = mutable.value.copy(webPairing = null) }
    fun refresh() = perform { apply(request("snapshot")) }
    fun invite(input: String) = perform {
        val peer = FamilyEndpoint().account(input) ?: error("invalid_member")
        apply(request("invite", "peer" to peer))
    }
    fun acceptMember(peer: String) = perform { apply(request("accept_member", "peer" to peer)) }
    fun removeMember(peer: String) = perform { apply(request("remove_member", "peer" to peer)) }
    fun call(peer: String, video: Boolean) = perform(callAction = true) {
        mutable.value = mutable.value.copy(error = null, callError = null)
        check(mutable.value.call == null)
        check(mutable.value.members.any { it.jid == peer && FamilyPolicy().canCall(it.status) })
        apply(request("invite_call", "peer" to peer, "kind" to if (video) "video" else "voice",
            "callId" to UUID.randomUUID().toString()))
    }
    fun acceptCall() = perform(callAction = true) {
        mutable.value.call?.let { apply(request("accept_call", "callId" to it.id)) }
    }
    fun hangup(origin: String = "in-app") = scope.launch {
        val call = mutable.value.call ?: return@launch
        android.util.Log.i("FamilyCalls", "Hangup requested: origin=$origin; stage=${call.stage}")
        finish(call.id, "local-hangup")
        runCatching { request("end_call", "callId" to call.id) }
    }
    fun previewCamera(callId: String? = null) {
        val call = mutable.value.call ?: return
        if (call.stage != "ringing" || call.kind != "video" || call.caller != mutable.value.me || (callId != null && call.id != callId)) return
        if (!mutable.value.camera) { mediaInstance?.close(); return }
        runCatching { media.preview() }.onSuccess {
            mutable.value = mutable.value.copy(callError = null)
        }.onFailure {
            mediaInstance?.close()
            android.util.Log.w("FamilyCalls", "Camera preview failed: ${it.javaClass.simpleName}")
            mutable.value = mutable.value.copy(callError = "Camera preview could not start. Cancel and retry the video call.")
        }
    }
    fun stopPreview(callId: String) {
        if (mutable.value.call?.let { it.id == callId && it.stage == "ringing" } == true) mediaInstance?.close()
    }
    fun mute() { val mute = !mutable.value.muted; media.mute(mute); mutable.value = mutable.value.copy(muted = mute) }
    fun toggleCamera() { val enabled = !mutable.value.camera; media.camera(enabled); mutable.value = mutable.value.copy(camera = enabled) }
    fun switchCamera() { media.switchCamera() }
    fun speaker() { val enabled = !mutable.value.speaker; media.speaker(enabled); mutable.value = mutable.value.copy(speaker = enabled) }

    private fun perform(callAction: Boolean = false, action: suspend () -> Unit) = scope.launch {
        val token = generation
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (token == generation) mutable.value = if (callAction && mutable.value.call != null)
                mutable.value.copy(callError = friendly(e.message)) else mutable.value.copy(error = friendly(e.message))
        }
    }
    private suspend fun request(action: String, vararg values: Pair<String, Any>): JSONObject {
        val activity = withContext(Dispatchers.Main.immediate) { Triple(foreground, activityVersion, callReducer.snapshotRevision) }
        return withContext(Dispatchers.IO) {
            val socket = connection ?: error("not_connected")
            val token = generation
            val body = JSONObject().put("v", 1).put("action", action)
            if (action == "snapshot" || action == "presence") body.put("active", activity.first).put("activityVersion", activity.second)
            values.forEach { body.put(it.first, it.second) }
            val iq = FamilyIq(body.toString()).also { it.to = org.jxmpp.jid.impl.JidCreate.domainBareFrom(FAMILY_DOMAIN) }
            val response = try {
                socket.createStanzaCollectorAndSend(iq).nextResultOrThrow<FamilyIq>()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (action == "snapshot") {
                    withContext(Dispatchers.Main.immediate) {
                        if (token == generation) mutable.value = mutable.value.copy(serviceReady = false)
                    }
                    error("family_unavailable")
                }
                throw e
            }
            check(token == generation && mutable.value.connected) { "not_connected" }
            JSONObject(response.body).also {
                if (!it.optBoolean("ok")) error(it.optString("error", "unavailable"))
                // Local request context, never taken from a server-supplied field.
                it.put("_localCallRevision", activity.third)
            }
        }
    }
    internal suspend fun apply(body: JSONObject) {
        presenceCache.beginEpoch(body.optString("presenceEpoch"))
        if (body.has("members")) {
            val rows = body.getJSONArray("members")
            val members = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val jid = row.getString("jid"); val status = row.getString("status")
                if (status == "accepted") presenceCache.apply(jid, row.optString("presence"), row.optLong("presenceVersion"))
                FamilyMember(jid, status)
            }
            presenceCache.retain(members.filter { it.status == "accepted" }.map { it.jid })
            mutable.value = mutable.value.copy(serviceReady = true, members = members.map { it.copy(presence = presenceCache.status(it.jid)) })
        } else if (body.optString("event") == "presence") {
            val jid = body.optString("jid")
            if (mutable.value.members.any { it.jid == jid && it.status == "accepted" }) {
                presenceCache.apply(jid, body.optString("presence"), body.optLong("presenceVersion"))
            }
            mutable.value = mutable.value.copy(members = mutable.value.members.map { it.copy(presence = presenceCache.status(it.jid)) })
        }
        body.optJSONObject("call")?.let { updateCall(it) }
        body.optJSONArray("calls")?.let { calls ->
            if (calls.length() == 0) {
                if (callReducer.canClearFromSnapshot(body.optLong("_localCallRevision", -1)))
                    mutable.value.call?.let { finish(it.id, "current-empty-snapshot") }
            } else updateCall(calls.getJSONObject(0))
        }
    }
    private suspend fun event(body: JSONObject) {
        if (!mutable.value.connected || body.optInt("v") != 1) return
        if (body.optString("event") == "signal") {
            val id = body.optString("callId")
            if (mutable.value.call?.id == id && id !in terminal) {
                val payload = body.optJSONObject("payload") ?: return
                if (rtcCallId == id && media.ready) runCatching { media.receive(payload) }
                    .onFailure { if (mutable.value.call?.id == id) endWithError("Could not negotiate this call’s media.") }
                else if (queuedSignals.size < 64) queuedSignals.add(id to payload)
            }
        } else apply(body)
    }
    private suspend fun updateCall(value: JSONObject) {
        val call = FamilyCall(value.getString("id"), value.getString("caller"), value.getString("callee"),
            value.getString("kind"), value.getString("stage"), value.getLong("version"),
            value.getLong("expires"), value.optBoolean("selected"))
        if (call.id in terminal) return
        val current = mutable.value.call
        if (current != null && (current.id != call.id || current.version >= call.version)) return
        if (!callReducer.apply(call.id, call.version, call.stage, call.expires, System.currentTimeMillis())) return
        // Fixed enums/numbers only. Never log JIDs, payloads, credentials or SDP.
        val reason = value.optString("reason").takeIf { it in setOf("hangup", "expired", "family_removed", "") } ?: "other"
        android.util.Log.i("FamilyCalls", "Call update: stage=${call.stage}; version=${call.version}; selected=${call.selected}; remainingMs=${call.expires - System.currentTimeMillis()}; serverReason=$reason")
        if (callReducer.stage == "ended" || (call.stage == "active" && !call.selected)) { finish(call.id, "terminal-expired-or-other-device"); return }
        if (call.stage == "ringing" && call.expires <= System.currentTimeMillis()) { finish(call.id, "terminal-expired-or-other-device"); return }
        mutable.value = mutable.value.copy(call = call, speaker = if (call.stage == "active") call.kind == "video" else mutable.value.speaker)
        // Acceptance, rather than ICE connectivity, ends Telecom's ringing/dialling
        // state. Waiting for media here can leave audio focus locked by the system.
        if (call.stage == "active") FamilyTelecom.accepted(call.id)
        FamilyCallService.ensure(context, microphone = call.stage == "active" && hasForegroundActivity,
            camera = call.stage == "active" && call.kind == "video" && hasForegroundActivity)
        if (call.stage == "ringing") {
            ringingJob?.cancel()
            ringingJob = scope.launch { delay((call.expires - System.currentTimeMillis()).coerceAtLeast(0));
                if (mutable.value.call?.id == call.id && mutable.value.call?.stage == "ringing") finish(call.id, "ring-timeout") }
        }
        if (call.stage == "active" && rtcCallId != call.id) {
            ringingJob?.cancel(); rtcCallId = call.id
            var startupStep = "relay-credentials"
            try {
                android.util.Log.i("FamilyCalls", "Call startup: step=$startupStep")
                val servers = request("turn", "callId" to call.id).getJSONArray("iceServers")
                if (mutable.value.call?.id != call.id) return
                startupStep = "native-initialization"
                val activeMedia = media
                startupStep = "native-media"
                activeMedia.start(servers, call.kind == "video", call.caller == mutable.value.me)
                startupStep = "queued-signals"
                val pending = queuedSignals.filter { it.first == call.id }; queuedSignals.clear()
                pending.forEach { media.receive(it.second) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val step = if (startupStep == "native-media") mediaInstance?.startupStep ?: startupStep else startupStep
                // Exception messages may contain protocol data. Only print known server codes.
                val code = error.message?.takeIf { it in setOf("not_active", "call_not_found", "not_family",
                    "not_connected", "invalid_signal", "invalid_request", "unavailable") } ?: "none"
                android.util.Log.w("FamilyCalls", "Call startup failed: step=$step; type=${error.javaClass.simpleName}; serverCode=$code")
                if (mutable.value.call?.id == call.id) endWithError(if (error is FamilyAudioFocus.Unavailable)
                    "Call audio is unavailable. End other calls, keep Deenly open, and try again."
                    else "Could not start this call. Check microphone/camera permissions and relay configuration.")
            }
        }
    }
    private fun finish(id: String, reason: String = "media-failure") {
        callReducer.finishCall(id)
        terminal.add(id); while (terminal.size > 256) terminal.remove(terminal.first())
        if (mutable.value.call?.id == id) {
            android.util.Log.i("FamilyCalls", "Ending local call presentation: $reason")
            mutable.value = mutable.value.copy(call = null, muted = false, camera = true, speaker = false, mediaConnected = false, connectedAtElapsed = null, callError = null)
            ringingJob?.cancel(); mediaInstance?.close(); rtcCallId = null; queuedSignals.clear()
        }
    }
    private suspend fun endWithError(message: String) {
        android.util.Log.w("FamilyCalls", "Media/signalling failure: hadCall=${mutable.value.call != null}")
        val call = mutable.value.call
        call?.let { finish(it.id); runCatching { request("end_call", "callId" to it.id) } }
        mutable.value = mutable.value.copy(error = message)
    }
    private fun signInError(error: Exception): String {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        return when {
            causes.any { it is org.jivesoftware.smack.sasl.SASLErrorException } ->
                "The family account or password was rejected. Use the password created on your server."
            causes.any { it is javax.net.ssl.SSLException } ->
                "The server certificate could not be verified. Check the device clock and server certificate."
            else -> "Could not reach the family server. Check that this device’s network is allowed through the server firewall on TCP 5222."
        }
    }
    private fun friendly(code: String?) = when (code) {
        "web_helper_unavailable" -> "Temporary call alerts are not enabled on the server yet."
        "family_unavailable" -> "Signed in, but the Family service did not respond. Tap Refresh to retry."

        "not_family" -> "Only accepted family members can call each other."
        "busy" -> "One of you is already in a call."
        "member_not_found", "invalid_member" -> "Enter a valid family account on this server."
        "invitation_not_found", "accept_invitation_first" -> "Accept the pending invitation first."
        "call_ended", "answered_elsewhere" -> "This call has ended or was answered on another device."
        else -> "Could not complete this action. Refresh your family list and try again."
    }
}
