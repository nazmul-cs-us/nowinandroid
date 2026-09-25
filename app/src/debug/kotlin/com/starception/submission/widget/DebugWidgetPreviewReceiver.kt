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

package com.starception.submission.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders every widget at its placed size to a PNG, for use as its picker preview.
 *
 * The picker previews were hand-built layouts that only resembled the widget — a location
 * row, a progress bar, colours picked by eye. The widget can render itself at any size
 * through Glance's `compose`, and [rasterise] already turns those RemoteViews into a
 * bitmap for the flipper pages, so the preview can simply *be* the widget.
 *
 * Sizes are the real ones this launcher hands out: a column is about 81dp and a row about
 * 111dp on the test device, measured from a placed 5x3 reporting 406x334.
 *
 * Debug-only, like [DebugResizeWidgetReceiver] — it writes to external files and must not
 * ship.
 *
 *   adb shell am broadcast -a com.starception.submission.DEBUG_RENDER_PREVIEWS \
 *     -n com.starception.submission.demo.debug/com.starception.submission.widget.DebugWidgetPreviewReceiver
 *   adb pull /sdcard/Android/data/com.starception.submission.demo.debug/files/previews
 */
class DebugWidgetPreviewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val dir = File(context.getExternalFilesDir(null), "previews").apply { mkdirs() }
            widgetPreviewSpecs.forEach { spec ->
                runCatching {
                    val views = spec.previewRemoteViews(context, slot = 3)
                    val bitmap = withContext(Dispatchers.Main) {
                        rasterise(context, views, spec.size)
                    }
                    if (bitmap == null) {
                        Log.w("WidgetPreview", "${spec.key} rasterised to nothing")
                        return@runCatching
                    }
                    File(dir, "preview_${spec.key}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    Log.i(
                        "WidgetPreview",
                        "${spec.key} -> ${bitmap.width}x${bitmap.height}",
                    )
                }.onFailure { Log.e("WidgetPreview", "${spec.key} failed", it) }
            }
            pending.finish()
        }
    }
}
