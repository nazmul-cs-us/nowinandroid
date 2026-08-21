/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.widget.samples.collections.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.glance.GlanceId
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.starception.submission.feature.quran.surahArtworkRes
import com.starception.submission.widget.samples.utils.AspectRatio
import com.starception.submission.widget.samples.utils.AspectRatio.Companion.asDouble
import com.starception.submission.widget.samples.collections.layout.ImageGridItemData
import com.starception.submission.widget.samples.utils.ImageUtils.getMaxPossibleImageSize
import com.starception.submission.widget.samples.utils.ImageUtils.getMaxWidgetMemoryAllowedSizeInBytes
import com.starception.submission.feature.quran.QuranData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.starception.submission.widget.samples.computeIfAbsent as computeIfAbsentExt
/**
 * A fake in-memory implementation of repository that produces a list of
 * [ImageGridItemData].
 *
 * During the data mapping, loads resources as bitmaps, and scales them down to size within the
 * limits allowed for widgets.
 *
 */
class FakeImageGridDataRepository {
  private val data = MutableStateFlow(listOf<ImageGridItemData>())
  private var items = demoItems.take(MAX_ITEMS_PER_WIDGET)

  /**
   * Flow of list of [ImageGridItemData]s that can be listened to during a Glance session.
   */
  fun data(): Flow<List<ImageGridItemData>> = data

  suspend fun refresh(context: Context) {
    // Pick a fresh set from all chapters instead of only reordering the eight already shown.
    items = demoItems.shuffled().take(MAX_ITEMS_PER_WIDGET)

    this.load(context)
  }

  /**
   * Loads the [ImageGridItemData] items from the currently selected data source.
   *
   * User selected data source is set by configuration activity via [selectDataSource].
   */
  suspend fun load(context: Context): List<ImageGridItemData> {
    data.value = processImagesAndBuildData(
      context = context,
      items = items
    )

    return data.value
  }

  private suspend fun processImagesAndBuildData(
    context: Context,
    items: List<ImageGridItemBackendData>,
  ): List<ImageGridItemData> {
    val maxAllowedBytes = context.getMaxWidgetMemoryAllowedSizeInBytes()
    val maxAllowedBytesPerImage = maxAllowedBytes / items.size
    val imageSizeLimit = getMaxPossibleImageSize(
      aspectRatio = AspectRatio.Ratio16x9.asDouble(),
      memoryLimitBytes = maxAllowedBytesPerImage,
      maxImages = 1
    )

    val width = IMAGE_SIZE.coerceAtMost(imageSizeLimit.width)
    val height = width * 9 / 16

    val mappedItems = coroutineScope {
      items.map { item ->
        async(Dispatchers.IO) {
          var bitmap: Bitmap? = null

          val result = ImageLoader(context).execute(
            ImageRequest.Builder(context)
              .data(item.imageRes)
              .size(width, height)
              .target { res: Drawable ->
                bitmap = (res as BitmapDrawable).bitmap
              }.build()
          )

          if (result is ErrorResult) {
            Log.e(TAG, "Failed to load the image:", result.throwable)
          }

          return@async ImageGridItemData(
            key = item.key,
            title = item.title,
            supportingText = item.supportingText,
            image = bitmap,
            imageContentDescription = item.imageContentDescription,
            surahNumber = item.key.toIntOrNull(),
          )
        }
      }.awaitAll()
    }

    return mappedItems
  }

  private data class ImageGridItemBackendData(
    val key: String,
    @DrawableRes val imageRes: Int,
    val imageContentDescription: String?,
    val title: String? = null,
    val supportingText: String? = null,
  )

    companion object {
        private val repositories = mutableMapOf<GlanceId, FakeImageGridDataRepository>()

        // Chapter-specific, bundled 16:9 artwork. Keeping the images local makes the
        // Quran widget deterministic offline and avoids replacing meaningful scenes with
        // whichever remote photo happened to remain cached by the launcher.

        private val ayahCounts = intArrayOf(
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99,
            128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38,
            29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18,
            12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29,
            19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8,
            11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
        )

        private val demoItems = QuranData.surahs.mapIndexed { index, surah ->
            ImageGridItemBackendData(
                key = surah.number.toString(),
                imageRes = surahArtworkRes(surah.number),
                imageContentDescription = "Symbolic artwork for Surah ${surah.nameEnglish}",
                title = "Surah ${surah.nameEnglish}",
                supportingText = "${ayahCounts[index]} ayahs · ${surah.revelationType}",
            )
        }
    /**
     * Returns the repository instance for the given widget represented by [glanceId].
     */
    fun getImageGridDataRepo(glanceId: GlanceId): FakeImageGridDataRepository {
      return synchronized(repositories) {
        repositories.computeIfAbsentExt(glanceId) { FakeImageGridDataRepository() }!!
      }
    }

    /**
     * Cleans up local data associated with the provided [glanceId].
     */
    fun cleanUp(glanceId: GlanceId) {
      synchronized(repositories) {
        repositories.remove(glanceId)
      }
    }

    // Capped at 8 to limit amount of memory consumed by bitmaps
    const val MAX_ITEMS_PER_WIDGET = 8
    const val IMAGE_SIZE = 200
    const val TAG = "FIGDR"
  }
}
