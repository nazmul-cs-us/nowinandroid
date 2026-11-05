package com.starception.submission.family

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

internal object FamilyTelecom {
    private var connection: Connection? = null
    private var announced: String? = null
    fun handle(context: Context) = PhoneAccountHandle(ComponentName(context, FamilyConnectionService::class.java), "family")
    fun register(context: Context) {
        runCatching { context.getSystemService(TelecomManager::class.java).registerPhoneAccount(
            PhoneAccount.builder(handle(context), "Family calls").setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP)).build()) }
    }
    fun update(context: Context, state: FamilyUiState) {
        val call = state.call ?: return
        if (announced != call.id) {
            announced = call.id
            val manager = context.getSystemService(TelecomManager::class.java)
            val extras = Bundle().apply { putString("callId", call.id) }
            runCatching {
                if (call.callee == state.me && call.stage == "ringing") manager.addNewIncomingCall(handle(context), extras)
                else if (call.caller == state.me) manager.placeCall(Uri.fromParts("sip", call.peer(state.me), null), Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(context))
                    putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras)
                })
            }
        }
        if (state.mediaConnected) connection?.setActive()
    }
    fun attach(value: Connection) { connection = value }
    fun end() { connection?.setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); connection?.destroy(); connection = null; announced = null }
}

@AndroidEntryPoint
class FamilyConnectionService : ConnectionService() {
    @Inject lateinit var client: FamilyClient
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
            private fun endCurrent() { if (client.state.value.call?.id == call.id) client.hangup() }
            override fun onReject() { endCurrent() }
            override fun onDisconnect() { endCurrent() }
            override fun onAbort() { endCurrent() }
        }.apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            connectionCapabilities = Connection.CAPABILITY_MUTE
            setAudioModeIsVoip(true)
            setAddress(Uri.fromParts("sip", call.peer(client.state.value.me), null), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(call.peer(client.state.value.me).substringBefore('@'), TelecomManager.PRESENTATION_ALLOWED)
            if (incoming) setRinging() else setDialing()
            FamilyTelecom.attach(this)
        }
    }
}
