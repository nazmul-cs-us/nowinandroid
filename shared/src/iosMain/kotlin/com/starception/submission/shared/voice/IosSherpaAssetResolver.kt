/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.voice

import com.starception.submission.shared.assets.iosCloudAssets
import com.starception.submission.shared.settings.VoiceRecognitionMode

internal object IosSherpaAssetResolver {
    suspend fun recognition(
        mode: VoiceRecognitionMode,
        onProgress: (Float) -> Unit,
    ): IosSherpaRecognitionPaths? {
        val prefix = if (mode == VoiceRecognitionMode.KEYWORDS) "models/kws" else "models/sherpa"
        val category = if (mode == VoiceRecognitionMode.KEYWORDS) "model_kws" else "model_asr"
        val result = iosCloudAssets.downloadCategory(category) { onProgress(it.fraction) }
        if (!result.isComplete) return null
        return IosSherpaRecognitionPaths(
            encoderPath = resolve("$prefix/encoder.int8.onnx") ?: return null,
            decoderPath = resolve("$prefix/decoder.int8.onnx") ?: return null,
            joinerPath = resolve("$prefix/joiner.int8.onnx") ?: return null,
            tokensPath = resolve("$prefix/tokens.txt") ?: return null,
            keywordsPath = if (mode == VoiceRecognitionMode.KEYWORDS) {
                resolve("$prefix/keywords.txt") ?: return null
            } else {
                ""
            },
        )
    }

    suspend fun tts(
        voiceIdentifier: String,
        onProgress: (Float) -> Unit,
    ): IosSherpaTtsPaths? {
        return if (voiceIdentifier == VITS_VOICE_ID) {
            val result = iosCloudAssets.downloadCategory("model_tts_vits") { onProgress(it.fraction) }
            if (!result.isComplete) return null
            IosSherpaTtsPaths(
                modelPath = resolve("models/tts/vits-vctk/vits-vctk.int8.onnx") ?: return null,
                tokensPath = resolve("models/tts/vits-vctk/tokens.txt") ?: return null,
                dataDirPath = "",
                lexiconPath = resolve("models/tts/vits-vctk/lexicon.txt") ?: return null,
            )
        } else {
            val result = iosCloudAssets.downloadCategory("model_tts_kokoro") { onProgress(it.fraction) }
            if (!result.isComplete) return null
            val model = resolve("$KOKORO_PREFIX/model.int8.onnx") ?: return null
            IosSherpaTtsPaths(
                modelPath = model,
                tokensPath = resolve("$KOKORO_PREFIX/tokens.txt") ?: return null,
                dataDirPath = model.substringBeforeLast('/') + "/espeak-ng-data",
                voicesPath = resolve("$KOKORO_PREFIX/voices.bin") ?: return null,
                language = "en-us",
            )
        }
    }

    private suspend fun resolve(cdnKey: String): String? =
        iosCloudAssets.resolveAsset(cdnKey)?.absolutePath

    const val KOKORO_VOICE_ID = "SHERPA_KOKORO"
    const val VITS_VOICE_ID = "SHERPA_VITS_VCTK"
    private const val KOKORO_PREFIX = "models/tts/kokoro-int8-en-v0_19"
}
