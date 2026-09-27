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
    ): IosSherpaRecognitionPaths? {
        val prefix = if (mode == VoiceRecognitionMode.KEYWORDS) "models/kws" else "models/sherpa"
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
    ): IosSherpaTtsPaths? {
        return if (voiceIdentifier == VITS_VOICE_ID) {
            IosSherpaTtsPaths(
                modelPath = resolve("models/tts/vits-vctk/vits-vctk.int8.onnx") ?: return null,
                tokensPath = resolve("models/tts/vits-vctk/tokens.txt") ?: return null,
                dataDirPath = "",
                lexiconPath = resolve("models/tts/vits-vctk/lexicon.txt") ?: return null,
            )
        } else {
            val model = resolve("$KOKORO_PREFIX/model.int8.onnx") ?: return null
            IosSherpaTtsPaths(
                modelPath = model,
                tokensPath = resolve("$KOKORO_PREFIX/tokens.txt") ?: return null,
                // Two layers read this one value with different expectations:
                // sherpa's Kokoro Validate checks <data_dir>/phontab (the
                // espeak-ng-data directory itself) while the bundled espeak-ng
                // resolves <data_dir>/espeak-ng-data. A self-referential
                // symlink inside the data dir satisfies both — no duplicate
                // files, and both path shapes resolve the same phontab.
                dataDirPath = ensureEspeakNestedLink(
                    model.substringBeforeLast('/') + "/espeak-ng-data",
                ),
                voicesPath = resolve("$KOKORO_PREFIX/voices.bin") ?: return null,
                language = "en-us",
            )
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun ensureEspeakNestedLink(espeakDataDir: String): String {
        // sherpa's Kokoro Validate checks <data_dir>/phontab while the bundled
        // espeak-ng resolves <data_dir>/espeak-ng-data. A nested real copy of
        // the data dir satisfies both path shapes (symlinks are rejected in
        // this container, so a one-time recursive copy it is).
        val nested = "$espeakDataDir/espeak-ng-data"
        val manager = platform.Foundation.NSFileManager.defaultManager()
        if (!manager.fileExistsAtPath("$nested/phontab")) {
            runCatching { manager.removeItemAtPath(nested, error = null) }
            manager.createDirectoryAtPath(
                nested,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            // Flat files only: copying the whole directory would recurse
            // into the destination we are creating inside the source.
            val entries = (
                manager.contentsOfDirectoryAtPath(espeakDataDir, error = null) as? List<String>
                )?.filter { it != "espeak-ng-data" } ?: emptyList()
            entries.forEach { entry ->
                runCatching {
                    manager.copyItemAtPath("$espeakDataDir/$entry", toPath = "$nested/$entry", error = null)
                }
            }
            println(
                "[SherpaTTS] nested espeak copy: ${entries.size} files " +
                    "(phontab=${manager.fileExistsAtPath("$nested/phontab")})",
            )
        }
        return espeakDataDir
    }

    private suspend fun resolve(cdnKey: String): String? =
        // resolveAsset (not lookupAsset): downloads from the CDN on first use
        // instead of silently returning null when a voice model is missing.
        iosCloudAssets.resolveAsset(cdnKey)?.absolutePath

    const val KOKORO_VOICE_ID = "SHERPA_KOKORO"
    const val VITS_VOICE_ID = "SHERPA_VITS_VCTK"
    private const val KOKORO_PREFIX = "models/tts/kokoro-int8-en-v0_19"
}
