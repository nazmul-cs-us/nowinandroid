package com.starception.submission.family

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

internal data class FamilyTelecomAudioState(val callId: String? = null, val managed: Boolean = false,
    val accepted: Boolean = false, val granted: Boolean = false)

internal object FamilyTelecom {
    private var connection: Connection? = null
    private var connectionCall: String? = null
    private var connectionService: ConnectionService? = null
    private var focusOwner: ConnectionService? = null
    private var managed = false
    private val mutableAudio = MutableStateFlow(FamilyTelecomAudioState())
    val audioFocus = mutableAudio.asStateFlow()
    private var announced: String? = null
    private var acceptedCall: String? = null
    fun handle(context: Context) = PhoneAccountHandle(ComponentName(context, FamilyConnectionService::class.java), "family")
    fun register(context: Context) {
        runCatching { context.getSystemService(TelecomManager::class.java).registerPhoneAccount(
            PhoneAccount.builder(handle(context), "Family calls").setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP)).build()) }
            .onFailure { android.util.Log.w("FamilyCalls", "Telecom registration failed: ${it.javaClass.simpleName}") }
    }
    fun update(context: Context, state: FamilyUiState) {
        val call = state.call ?: return
        if (announced != call.id) {
            announced = call.id
            managed = false
            val manager = context.getSystemService(TelecomManager::class.java)
            val extras = Bundle().apply { putString("callId", call.id) }
            runCatching {
                if (call.callee == state.me && call.stage == "ringing") {
                    manager.addNewIncomingCall(handle(context), extras); managed = true
                } else if (call.caller == state.me) {
                    manager.placeCall(Uri.fromParts("sip", call.peer(state.me), null), Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(context))
                        putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras)
                    }); managed = true
                }
            }.onFailure { android.util.Log.w("FamilyCalls", "Telecom announcement failed: ${it.javaClass.simpleName}") }
        }
        if (call.stage == "active") accepted(call.id)
        publishAudio()
    }
    fun accepted(callId: String) {
        if (announced != null && announced != callId) return
        acceptedCall = callId
        if (connectionCall == callId && connection?.state != Connection.STATE_ACTIVE) connection?.setActive()
        publishAudio()
    }
    fun attach(value: Connection, callId: String, service: ConnectionService? = null) {
        if (announced != callId) {
            value.setDisconnected(DisconnectCause(DisconnectCause.CANCELED)); value.destroy(); return
        }
        connection = value
        connectionCall = callId
        connectionService = service
        managed = true
        if (acceptedCall == callId) value.setActive()
        publishAudio()
    }
    private fun publishAudio() {
        mutableAudio.value = FamilyTelecomAudioState(announced, managed,
            announced != null && acceptedCall == announced,
            connectionCall == announced && connectionService != null && focusOwner === connectionService)
    }
    fun focusGained(service: ConnectionService) {
        if (connectionService != null && connectionService !== service) return
        focusOwner = service
        android.util.Log.i("FamilyCalls", "Telecom call audio focus granted")
        publishAudio()
    }
    fun focusLost(service: ConnectionService): Boolean {
        if (focusOwner !== service) return false
        focusOwner = null
        android.util.Log.i("FamilyCalls", "Telecom call audio focus lost")
        publishAudio()
        return connectionService === service
    }
    fun serviceDestroyed(service: ConnectionService) {
        if (focusOwner === service) focusOwner = null
        if (connectionService === service) connectionService = null
        publishAudio()
    }
    fun end(reason: String = "service-idle") {
        android.util.Log.i("FamilyCalls", "Telecom cleanup: reason=$reason; hadConnection=${connection != null}")
        connection?.setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); connection?.destroy(); connection = null; announced = null; acceptedCall = null
        connectionCall = null; connectionService = null; managed = false
        publishAudio()
    }
}

@AndroidEntryPoint
class FamilyConnectionService : ConnectionService() {
    @Inject lateinit var client: FamilyClient
    override fun onConnectionServiceFocusGained() { FamilyTelecom.focusGained(this) }
    override fun onConnectionServiceFocusLost() {
        if (FamilyTelecom.focusLost(this)) client.hangup("system-audio-focus-lost")
        // These callbacks run on main; hangup releases capture before its first suspension.
        connectionServiceFocusReleased()
    }
    override fun onDestroy() { FamilyTelecom.serviceDestroyed(this); super.onDestroy() }
    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, request: ConnectionRequest?): Connection = create(request, true)
    override fun onCreateOutgoingConnection(account: PhoneAccountHandle?, request: ConnectionRequest?): Connection = create(request, false)
    private fun create(request: ConnectionRequest?, incoming: Boolean): Connection {
        val call = client.state.value.call ?: return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
        if (incoming && request?.extras?.getString("callId") != call.id)
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
        return object : Connection() {
            override fun onAnswer() { answer() }
            override fun onAnswer(videoState: Int) { answer() }
            private fun answer() {
                if (client.state.value.call?.id != call.id) return
                startActivity(Intent(this@FamilyConnectionService, FamilyIncomingActivity::class.java)
                    .putExtra("callId", call.id).putExtra("answer", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            private fun endCurrent(origin: String) {
                android.util.Log.i("FamilyCalls", "Telecom callback: $origin; matchesCurrent=${client.state.value.call?.id == call.id}")
                if (client.state.value.call?.id == call.id) client.hangup(origin)
            }
            override fun onReject() { endCurrent("system-reject") }
            override fun onDisconnect() { endCurrent("system-disconnect") }
            override fun onAbort() { endCurrent("system-abort") }
        }.apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            connectionCapabilities = Connection.CAPABILITY_MUTE
            setAudioModeIsVoip(true)
            setAddress(Uri.fromParts("sip", call.peer(client.state.value.me), null), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(call.peer(client.state.value.me).substringBefore('@'), TelecomManager.PRESENTATION_ALLOWED)
            if (incoming) setRinging() else setDialing()
            FamilyTelecom.attach(this, call.id, this@FamilyConnectionService)
        }
    }
}
