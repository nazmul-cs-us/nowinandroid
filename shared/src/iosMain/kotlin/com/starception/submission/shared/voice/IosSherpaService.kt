package com.starception.submission.shared.voice

/** Resolved local files for a streaming transducer used by KWS or online ASR. */
class IosSherpaRecognitionPaths(
    val encoderPath: String,
    val decoderPath: String,
    val joinerPath: String,
    val tokensPath: String,
    val keywordsPath: String = "",
)

/**
 * Resolved local files for Kokoro or VITS TTS.
 *
 * A non-empty [voicesPath] selects Kokoro. An empty [voicesPath] selects VITS.
 */
class IosSherpaTtsPaths(
    val modelPath: String,
    val tokensPath: String,
    val dataDirPath: String,
    val voicesPath: String = "",
    val lexiconPath: String = "",
    val dictDirPath: String = "",
    val language: String = "",
)

/** Receives Sherpa recognition and playback events on the iOS main thread. */
interface IosSherpaEventSink {
    fun onRecognitionStarted()
    fun onKeyword(keyword: String)
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onTtsStarted(sampleRate: Int)
    fun onTtsFinished()
    fun onError(message: String)
}

/** Native iOS Sherpa runtime supplied by Swift and injected into shared Kotlin. */
interface IosSherpaService {
    fun startKeywordSpotting(
        paths: IosSherpaRecognitionPaths,
        eventSink: IosSherpaEventSink,
    ): Boolean

    fun startOnlineRecognition(
        paths: IosSherpaRecognitionPaths,
        eventSink: IosSherpaEventSink,
    ): Boolean

    fun stopRecognition()

    fun speak(
        text: String,
        paths: IosSherpaTtsPaths,
        speakerId: Int,
        speed: Float,
        eventSink: IosSherpaEventSink,
    ): Boolean

    fun stopSpeaking()

    fun shutdown()
}
