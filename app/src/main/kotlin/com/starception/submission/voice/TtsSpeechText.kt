package com.starception.submission.voice

import com.starception.submission.feature.quran.normalizeTafsirSpeechText
import com.starception.submission.settings.components.TtsVoice
import java.text.Normalizer

/** Bengali never passes through the English-only speech normalizer. Display text is untouched. */
internal fun normalizeTtsSpeechText(voice: TtsVoice, text: String): String =
    if (voice.languageCode == "bn") Normalizer.normalize(normalizeTafsirSpeechText(text, "bn"), Normalizer.Form.NFC)
    else EnglishTtsTextNormalizer.normalize(text)
