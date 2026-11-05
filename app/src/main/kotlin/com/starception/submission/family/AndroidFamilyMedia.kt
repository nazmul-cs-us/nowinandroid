package com.starception.submission.family

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native SDK objects stay outside shared protocol/domain code. */
class AndroidFamilyMedia(
    private val context: Context,
    private val scope: CoroutineScope,
    private val signal: (JSONObject) -> Unit,
    private val connected: (Boolean) -> Unit,
    private val failed: (String) -> Unit,
) {
    init {
        synchronized(AndroidFamilyMedia::class.java) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        }
    }
    val egl: EglBase = EglBase.create()
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var ownsAudio = false
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
    }
    private var peer: PeerConnection? = null
    private var microphone: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var cameraSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var texture: SurfaceTextureHelper? = null
    private var remoteDescription = false
    private val candidates = mutableListOf<IceCandidate>()
    private var epoch = 0
    val ready: Boolean get() = peer != null

    suspend fun start(servers: JSONArray, video: Boolean, caller: Boolean) {
        close(preservePreview = video && caller && peer == null && localVideo.value != null)
        val token = epoch
        FamilyAudioSession.setActive(true)
        previousMode = audio.mode; previousSpeaker = audio.isSpeakerphoneOn
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    scope.launch { if (token == epoch) failed("Call interrupted by another audio session.") }
                }
            }.build()
        check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        focus = request; ownsAudio = true
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        speaker(video)
        val iceServers = (0 until servers.length()).map { i ->
            val item = servers.getJSONObject(i); val urls = item.getJSONArray("urls")
            PeerConnection.IceServer.builder((0 until urls.length()).map { urls.getString(it) })
                .setUsername(item.getString("username")).setPassword(item.getString("credential")).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onIceCandidate(candidate: IceCandidate) { scope.launch {
                if (token == epoch) signal(JSONObject().put("type", "ice").put("candidate", candidate.sdp)
                    .put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex))
            } }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) { scope.launch {
                if (token != epoch) return@launch
                connected(state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED)
                if (state == PeerConnection.IceConnectionState.FAILED) failed("Could not connect media. Check the relay and network connection.")
            } }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) { scope.launch {
                if (token == epoch) remoteVideo.value = receiver?.track() as? VideoTrack
            } }
            override fun onTrack(transceiver: RtpTransceiver?) { scope.launch {
                if (token == epoch) remoteVideo.value = transceiver?.receiver?.track() as? VideoTrack
            } }
        }) ?: error("Could not create media session")
        audioSource = factory.createAudioSource(MediaConstraints())
        microphone = factory.createAudioTrack("family-audio", audioSource)
        peer!!.addTrack(microphone, listOf("family"))
        if (video) {
            preview()
            peer!!.addTrack(localVideo.value, listOf("family"))
        }
        if (caller) {
            val offer = create(true)
            if (token != epoch) return
            set(offer, true)
            if (token == epoch) signal(JSONObject().put("type", "offer").put("sdp", offer.description))
        }
    }
    /** Local-only camera. No peer, microphone or signalling is created. */
    fun preview() {
        if (localVideo.value != null) return
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(false)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.first()
        capturer = enumerator.createCapturer(name, null) ?: error("Camera unavailable")
        cameraSource = factory.createVideoSource(false)
        texture = SurfaceTextureHelper.create("FamilyCamera", egl.eglBaseContext)
        capturer!!.initialize(texture, context, cameraSource!!.capturerObserver)
        localVideo.value = factory.createVideoTrack("family-video", cameraSource)
        capturer!!.startCapture(640, 480, 24)
    }
    suspend fun receive(payload: JSONObject) {
        val token = epoch
        val connection = peer ?: return
        when (payload.optString("type")) {
            "ice" -> {
                val candidate = payload.optString("candidate")
                if (candidate.length !in 1..4096) return
                val ice = IceCandidate(payload.optString("sdpMid"), payload.optInt("sdpMLineIndex"), candidate)
                if (remoteDescription) connection.addIceCandidate(ice)
                else if (candidates.size < 256) candidates.add(ice)
            }
            "offer", "answer" -> {
                val sdp = payload.optString("sdp")
                if (sdp.length !in 1..35000) return
                val type = if (payload.getString("type") == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
                set(SessionDescription(type, sdp), false)
                if (token != epoch) return
                remoteDescription = true
                candidates.forEach { connection.addIceCandidate(it) }; candidates.clear()
                if (type == SessionDescription.Type.OFFER) {
                    val answer = create(false)
                    if (token != epoch) return
                    set(answer, true)
                    if (token == epoch) signal(JSONObject().put("type", "answer").put("sdp", answer.description))
                }
            }
        }
    }
    private suspend fun create(offer: Boolean): SessionDescription = suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
            override fun onCreateFailure(error: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not negotiate media")) }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }
        if (offer) peer!!.createOffer(observer, MediaConstraints()) else peer!!.createAnswer(observer, MediaConstraints())
    }
    private suspend fun set(sdp: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not apply media negotiation")) }
            override fun onCreateSuccess(sdp: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
        }
        if (local) peer!!.setLocalDescription(observer, sdp) else peer!!.setRemoteDescription(observer, sdp)
    }
    fun mute(muted: Boolean) { microphone?.setEnabled(!muted) }
    fun camera(enabled: Boolean) { localVideo.value?.setEnabled(enabled) }
    fun switchCamera() { capturer?.switchCamera(null) }
    @Suppress("DEPRECATION") fun speaker(enabled: Boolean) { audio.isSpeakerphoneOn = enabled }
    fun close(preservePreview: Boolean = false) {
        epoch++; remoteDescription = false; candidates.clear()
        if (!preservePreview) {
            runCatching { capturer?.stopCapture() }; capturer?.dispose(); capturer = null
            texture?.dispose(); texture = null
            localVideo.value?.dispose(); localVideo.value = null
            cameraSource?.dispose(); cameraSource = null
        }
        peer?.close(); peer?.dispose(); peer = null
        remoteVideo.value = null
        microphone?.dispose(); microphone = null
        audioSource?.dispose(); audioSource = null
        if (ownsAudio) {
            audio.mode = previousMode; speaker(previousSpeaker)
            focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null; ownsAudio = false
        }
        FamilyAudioSession.setActive(false)
        connected(false)
    }
}
