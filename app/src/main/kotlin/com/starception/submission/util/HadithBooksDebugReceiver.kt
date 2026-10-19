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

package com.starception.submission.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.ml.GeneratedHadithBooksStore
import com.starception.submission.ml.HadithBookCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ADB-triggered test hook for the on-device hadith-book generator, following
 * the DebugDrivingReceiver pattern. Runs the AI categorizer over a downloaded
 * collection database and persists the generated books for the browsing UI.
 *
 * Trigger (model + collection must already be downloaded):
 *
 *   adb shell am broadcast \
 *     -a com.starception.submission.DEBUG_GENERATE_HADITH_BOOKS \
 *     --es database "sunan_darimi.db" --ei samples 48
 *
 * Follow progress with:
 *   adb logcat -s HadithBookCategorizer HadithBooksDebugReceiver
 */
class HadithBooksDebugReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HadithBooksDebugReceiver"
        const val ACTION = "com.starception.submission.DEBUG_GENERATE_HADITH_BOOKS"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val appContext = context.applicationContext
        val databaseFile = intent.getStringExtra("database")
            ?: "sunan_darimi.db"
        val samples = intent.getIntExtra("samples", 48)

        if (!HadithBookCategorizer.isModelDownloaded(appContext)) {
            Log.w(TAG, "Categorizer model not downloaded (category " +
                "${HadithBookCategorizer.DOWNLOAD_CATEGORY}); download it first")
            return
        }

        Log.i(TAG, "🧪 Generating books for $databaseFile with $samples samples")
        scope.launch {
            val categorizer = HadithBookCategorizer(appContext)
            val books = categorizer.generateBooks(databaseFile, maxSamples = samples) { progress ->
                Log.i(
                    TAG,
                    "🧪 ${progress.phase}: ${progress.processed}/${progress.total}",
                )
            }
            if (books == null) {
                Log.w(TAG, "🧪 Book generation failed for $databaseFile")
                return@launch
            }
            Log.i(TAG, "🧪 Generated ${books.size} books for $databaseFile:")
            books.forEach { book ->
                Log.i(
                    TAG,
                    "🧪   ${book.id.toString().padStart(2)} ${book.nameEnglish} " +
                        "(${book.hadithCount} hadiths · ${book.firstHadithId}–${book.lastHadithId})",
                )
            }
            Log.i(
                TAG,
                "🧪 Saved to the generated store; re-open the topic to browse: " +
                    "${GeneratedHadithBooksStore.load(appContext, databaseFile)?.size} books",
            )
        }
    }
}
