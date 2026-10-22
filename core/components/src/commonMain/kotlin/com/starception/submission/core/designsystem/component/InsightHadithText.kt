/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.core.designsystem.component

private val hadithWhitespace = Regex("[\\s\u00a0\u2028\u2029]+")
private val hadithAttribution = Regex("^(Narrated [^:]{1,180}:) (.+)$")

/** Keep the wording while removing imported hard wrapping and repeated blank lines. */
fun formatInsightHadithText(raw: String): String {
    val normalized = raw.replace(hadithWhitespace, " ").trim()
    val narrated = hadithAttribution.matchEntire(normalized) ?: return normalized
    return narrated.groupValues[1] + "\n\n" + narrated.groupValues[2]
}
