package com.starception.submission.family

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class FamilyMember(val jid: String, val status: String)
data class FamilyCall(
    val id: String, val caller: String, val callee: String, val kind: String,
    val stage: String, val version: Long, val expires: Long, val selected: Boolean,
) { fun peer(me: String) = if (caller == me) callee else caller }
data class FamilyUiState(
    val me: String = "", val connected: Boolean = false, val busy: Boolean = false,
    val serviceReady: Boolean = false,
    val members: List<FamilyMember> = emptyList(), val call: FamilyCall? = null,
    val error: String? = null, val muted: Boolean = false, val camera: Boolean = true,
    val speaker: Boolean = false, val mediaConnected: Boolean = false,
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(FamilyUiState())
    val state = mutable.asStateFlow()
    private var connection: XMPPTCPConnection? = null
    private var generation = 0
    private var rtcCallId: String? = null
    private val callReducer = FamilyCallReducer()
    private val terminal = LinkedHashSet<String>()
    private val queuedSignals = mutableListOf<Pair<String, JSONObject>>()
    private var ringingJob: Job? = null
    private var heartbeat: Job? = null
    private var visibleActivities = 0
    init {
        (context.applicationContext as android.app.Application).registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: android.app.Activity) { visibleActivities++ }
                override fun onActivityStopped(activity: android.app.Activity) {
                    visibleActivities--
                    if (visibleActivities <= 0 && !activity.isChangingConfigurations) hangup()
                }
                override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) = Unit
                override fun onActivityResumed(a: android.app.Activity) = Unit
                override fun onActivityPaused(a: android.app.Activity) = Unit
                override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) = Unit
                override fun onActivityDestroyed(a: android.app.Activity) = Unit
            })
    }
    private var mediaInstance: AndroidFamilyMedia? = null
    val media: AndroidFamilyMedia get() = mediaInstance ?: createMedia().also { mediaInstance = it }
    private fun createMedia() = AndroidFamilyMedia(context, scope,
        signal = { payload -> mutable.value.call?.let { call ->
            scope.launch { runCatching { request("signal", "callId" to call.id, "payload" to payload) }
                .onFailure { endWithError("Call signalling was interrupted.") } }
        } },
        connected = { connected -> mutable.value = mutable.value.copy(mediaConnected = connected) },
        failed = { message -> scope.launch { endWithError(message) } },
    )

    fun login(jidInput: String, password: String) = scope.launch {
        if (mutable.value.busy) return@launch
        val jid = FamilyEndpoint().account(jidInput)
        if (jid == null || !jid.endsWith("@$FAMILY_DOMAIN") || password.isBlank()) {
            mutable.value = mutable.value.copy(error = "Enter your family account and password."); return@launch
        }
        disconnect()
        val token = generation
        mutable.value = FamilyUiState(me = jid, busy = true)
        var attemptedSocket: XMPPTCPConnection? = null
        try {
            ProviderManager.addIQProvider("family", FAMILY_NS, FamilyIqProvider())
            val socket = withContext(Dispatchers.IO) {
                XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(FAMILY_DOMAIN).setHost(FAMILY_DOMAIN).setPort(FamilyEndpoint().port)
                    .setUsernameAndPassword(jid.substringBefore('@'), password)
                    .setResource("android-${UUID.randomUUID()}")
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
                        override fun connectionClosed() { scope.launch { if (mutable.value.connected) lost(token) } }
                        override fun connectionClosedOnError(e: Exception) { scope.launch { if (mutable.value.connected) lost(token) } }
                    })
                    socket.connect().login()
                }
            }
            if (token != generation) { withContext(Dispatchers.IO) { socket.disconnect() }; return@launch }
            connection = socket
            mutable.value = mutable.value.copy(connected = true, busy = false)
            refresh()
            heartbeat = scope.launch {
                while (isActive) { delay(20000); runCatching { apply(request("snapshot")) } }
            }
        } catch (e: Exception) {
            if (token == generation) {
                generation++
                mutable.value = mutable.value.copy(busy = false, connected = false,
                    serviceReady = false, error = signInError(e))
            }
            withContext(Dispatchers.IO) { runCatching { attemptedSocket?.disconnect() } }
            if (e is CancellationException) throw e
        }
    }

    fun disconnect() {
        generation++
        heartbeat?.cancel(); heartbeat = null
        val oldCall = mutable.value.call
        val old = connection
        if (oldCall != null && old != null) scope.launch(Dispatchers.IO) {
            runCatching {
                val iq = FamilyIq(JSONObject().put("v", 1).put("action", "end_call").put("callId", oldCall.id).toString())
                iq.to = org.jxmpp.jid.impl.JidCreate.domainBareFrom(FAMILY_DOMAIN)
                old.createStanzaCollectorAndSend(iq).nextResultOrThrow<FamilyIq>()
            }
            runCatching { old.disconnect() }
        }
        connection = null
        ringingJob?.cancel(); mediaInstance?.close(); rtcCallId = null; queuedSignals.clear(); terminal.clear(); callReducer.clear()
        mutable.value = FamilyUiState()
        if (oldCall == null) scope.launch(Dispatchers.IO) { runCatching { old?.disconnect() } }
    }
    private fun lost(token: Int) {
        if (token != generation) return
        generation++
        heartbeat?.cancel(); heartbeat = null
        mediaInstance?.close(); rtcCallId = null; queuedSignals.clear(); ringingJob?.cancel()
        mutable.value = mutable.value.copy(connected = false, serviceReady = false, busy = false, call = null,
            error = "Connection lost. Sign in again before calling.")
    }
    fun dismissError() { mutable.value = mutable.value.copy(error = null) }
    fun refresh() = perform { apply(request("snapshot")) }
    fun invite(input: String) = perform {
        val peer = FamilyEndpoint().account(input) ?: error("invalid_member")
        apply(request("invite", "peer" to peer))
    }
    fun acceptMember(peer: String) = perform { apply(request("accept_member", "peer" to peer)) }
    fun removeMember(peer: String) = perform { apply(request("remove_member", "peer" to peer)) }
    fun call(peer: String, video: Boolean) = perform {
        check(mutable.value.call == null)
        check(mutable.value.members.any { it.jid == peer && FamilyPolicy().canCall(it.status) })
        apply(request("invite_call", "peer" to peer, "kind" to if (video) "video" else "voice",
            "callId" to UUID.randomUUID().toString()))
    }
    fun acceptCall() = perform {
        mutable.value.call?.let { apply(request("accept_call", "callId" to it.id)) }
    }
    fun hangup() = scope.launch {
        val call = mutable.value.call ?: return@launch
        finish(call.id)
        runCatching { request("end_call", "callId" to call.id) }
    }
    fun mute() { val mute = !mutable.value.muted; media.mute(mute); mutable.value = mutable.value.copy(muted = mute) }
    fun toggleCamera() { val enabled = !mutable.value.camera; media.camera(enabled); mutable.value = mutable.value.copy(camera = enabled) }
    fun switchCamera() { media.switchCamera() }
    fun speaker() { val enabled = !mutable.value.speaker; media.speaker(enabled); mutable.value = mutable.value.copy(speaker = enabled) }

    private fun perform(action: suspend () -> Unit) = scope.launch {
        val token = generation
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (token == generation) mutable.value = mutable.value.copy(error = friendly(e.message)) }
    }
    private suspend fun request(action: String, vararg values: Pair<String, Any>): JSONObject = withContext(Dispatchers.IO) {
        val socket = connection ?: error("not_connected")
        val token = generation
        val body = JSONObject().put("v", 1).put("action", action)
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
        JSONObject(response.body).also { if (!it.optBoolean("ok")) error(it.optString("error", "unavailable")) }
    }
    private suspend fun apply(body: JSONObject) {
        if (body.has("members")) {
            val members = body.getJSONArray("members")
            mutable.value = mutable.value.copy(serviceReady = true, members = (0 until members.length()).map { i ->
                members.getJSONObject(i).let { FamilyMember(it.getString("jid"), it.getString("status")) }
            })
        }
        body.optJSONObject("call")?.let { updateCall(it) }
        body.optJSONArray("calls")?.let { calls ->
            if (calls.length() == 0) mutable.value.call?.let { finish(it.id) }
            else updateCall(calls.getJSONObject(0))
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
        if (callReducer.stage == "ended" || (call.stage == "active" && !call.selected)) { finish(call.id); return }
        if (call.stage == "ringing" && call.expires <= System.currentTimeMillis()) { finish(call.id); return }
        mutable.value = mutable.value.copy(call = call, speaker = if (call.stage == "active") call.kind == "video" else mutable.value.speaker)
        if (call.stage == "ringing") {
            ringingJob?.cancel()
            ringingJob = scope.launch { delay((call.expires - System.currentTimeMillis()).coerceAtLeast(0));
                if (mutable.value.call?.id == call.id && mutable.value.call?.stage == "ringing") finish(call.id) }
        }
        if (call.stage == "active" && rtcCallId != call.id) {
            ringingJob?.cancel(); rtcCallId = call.id
            try {
                val servers = request("turn", "callId" to call.id).getJSONArray("iceServers")
                if (mutable.value.call?.id != call.id) return
                media.start(servers, call.kind == "video", call.caller == mutable.value.me)
                val pending = queuedSignals.filter { it.first == call.id }; queuedSignals.clear()
                pending.forEach { media.receive(it.second) }
            } catch (_: Exception) { if (mutable.value.call?.id == call.id) endWithError("Could not start this call. Check microphone/camera permissions and relay configuration.") }
        }
    }
    private fun finish(id: String) {
        callReducer.finishCall(id)
        terminal.add(id); while (terminal.size > 256) terminal.remove(terminal.first())
        if (mutable.value.call?.id == id) {
            mutable.value = mutable.value.copy(call = null, muted = false, camera = true, speaker = false, mediaConnected = false)
            ringingJob?.cancel(); mediaInstance?.close(); rtcCallId = null; queuedSignals.clear()
        }
    }
    private suspend fun endWithError(message: String) {
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
        "family_unavailable" -> "Signed in, but the Family service did not respond. Tap Refresh to retry."

        "not_family" -> "Only accepted family members can call each other."
        "busy" -> "One of you is already in a call."
        "member_not_found", "invalid_member" -> "Enter a valid family account on this server."
        "invitation_not_found", "accept_invitation_first" -> "Accept the pending invitation first."
        "call_ended", "answered_elsewhere" -> "This call has ended or was answered on another device."
        else -> "Could not complete this action. Refresh your family list and try again."
    }
}
