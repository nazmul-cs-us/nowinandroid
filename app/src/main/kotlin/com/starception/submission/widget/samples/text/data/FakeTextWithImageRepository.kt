package com.starception.submission.widget.samples.text.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.glance.GlanceId
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.starception.submission.widget.samples.utils.AspectRatio
import com.starception.submission.widget.samples.utils.AspectRatio.Companion.asDouble
import com.starception.submission.widget.samples.text.layout.ImageData
import com.starception.submission.widget.samples.text.layout.TextData
import com.starception.submission.widget.samples.text.layout.TextWithImageData
import com.starception.submission.widget.samples.utils.ImageUtils
import com.starception.submission.widget.samples.utils.ImageUtils.getMaxWidgetMemoryAllowedSizeInBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.starception.submission.widget.samples.computeIfAbsent as computeIfAbsentExt
/**
 * An fake in-memory repository to provide data for displaying different demo samples in
 * [com.starception.submission.widget.samples.text.layout.TextWithImageLayout]
 */
class FakeTextWithImageRepository {
  private val data = MutableStateFlow<TextWithImageData?>(null)
  private var itemIndex = 0
  private var items = demoItems

  fun data(): Flow<TextWithImageData?> = data

  suspend fun refresh(context: Context) {
    val itemCount = items.size
    itemIndex = (itemIndex + 1) % itemCount

    this.load(context)
  }

  suspend fun load(context: Context): TextWithImageData? {
    val item = items[itemIndex]
    val bitmap = fetchImage(context, item.url)
    val mappedImageData = ImageData(
      bitmap = bitmap,
      contentDescription = item.imageContentDescription
    )

    // Headline and caption come from the app's own generator so the card describes the
    // prayer that is actually current — the canned text said "Fajr" at Asr time. The
    // imagery stays as authored; only the words are ours.
    val live = com.starception.submission.widget.livePrayerInsight(context)

    data.value = TextWithImageData(
      textData = TextData(
        key = "$itemIndex",
        primary = live?.title ?: item.primary,
        // The layout gives this a narrow column and clips it; the sample's copy ran to
        // three lines and lost its last word to an ellipsis.
        secondary = live?.let { "${it.elapsed}. ${it.nextPrayerInfo}" } ?: item.secondary,
        caption = live?.caption ?: item.caption
      ),
      imageData = mappedImageData
    )
    return data.value
  }

  private suspend fun fetchImage(context: Context, url: String): Bitmap? {
    val maxAllowedBytes = context.getMaxWidgetMemoryAllowedSizeInBytes()
    val imageSizeLimit = ImageUtils.getMaxPossibleImageSize(
      aspectRatio = AspectRatio.Ratio16x9.asDouble(),
      memoryLimitBytes = maxAllowedBytes,
      maxImages = 1
    )
    val maxWidth = imageSizeLimit.width
    val maxHeight = imageSizeLimit.height

    var bitmap: Bitmap? = null

    val result = ImageLoader(context).execute(
      ImageRequest.Builder(context)
        .data(url)
        .size(maxWidth, maxHeight)
        .target { res: Drawable ->
          bitmap = (res as BitmapDrawable).bitmap
        }.build()
    )

    if (result is ErrorResult) {
      Log.e(TAG, "Failed to load the image:", result.throwable)
    }

    return bitmap
  }

  companion object {
    private const val TAG = "FTWIR"

    private val repositories = mutableMapOf<GlanceId, FakeTextWithImageRepository>()

    /**
     * Returns the repository instance for the given widget represented by [glanceId].
     */
    fun getRepo(glanceId: GlanceId): FakeTextWithImageRepository {
      return synchronized(repositories) {
        repositories.computeIfAbsentExt(glanceId) {
          FakeTextWithImageRepository()
        }!!
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

    data class DemoData(
      val url: String,
      val imageContentDescription: String? = null,
      val primary: String,
      val secondary: String,
      val caption: String,
    )

      // Courtesy of https://unsplash.com/@iamliam
      val demoItems = listOf(
          DemoData(
              primary = "Make time for Fajr",
              secondary = "The dawn prayer sets the tone for the whole day. Sleep earlier tonight and give yourself the best start tomorrow.",
              caption = "Fajr",
              url = "https://images.unsplash.com/photo-1671525737370-1d490286372e",
              imageContentDescription = "Davos at sunrise, viewed from Schatzalp",
          ),
          DemoData(
              primary = "Sunnah of Dhuhr",
              secondary = "Four units before Dhuhr and two after; a small consistency that the Prophet never left.",
              caption = "Dhuhr",
              url = "https://images.unsplash.com/photo-1531306760863-7fb02a41db12",
              imageContentDescription = "Flowers at a wedding reception",
          ),
          DemoData(
              primary = "Asr and the day's balance",
              secondary = "Guard the middle prayer. It falls when the day is busiest, which is exactly why it counts.",
              caption = "Asr",
              url = "https://images.unsplash.com/photo-1566964423430-3e52903303a5",
              imageContentDescription = "Blushing Bride flower",
          ),
          DemoData(
              primary = "Break your fast on time",
              secondary = "People remain upon good as long as they hasten to break the fast at Maghrib.",
              caption = "Maghrib",
              url = "https://images.unsplash.com/photo-1671525784444-392a8f8daa3f",
              imageContentDescription = "A snow-shoer walking up Strelapass on snow lined with deep trails from skiiers",
          ),
          DemoData(
              primary = "The night prayer",
              secondary = "The last third of the night is the quietest hour, and the closest one to your Lord. Set an alarm and try it once.",
              caption = "Isha",
              url = "https://images.unsplash.com/photo-1685540466252-8c21e7c37624",
              imageContentDescription = "A single water droplet rests in a budding red pansy.",
          )
      )
  }
}