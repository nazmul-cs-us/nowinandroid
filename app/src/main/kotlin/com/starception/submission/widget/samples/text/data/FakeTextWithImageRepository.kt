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

  /** When this widget's prayer reading was last rebuilt from live data. */
  private var lastLiveReadingMillis = 0L

  fun data(): Flow<TextWithImageData?> = data

  suspend fun refresh(context: Context) {
    val itemCount = items.size
    itemIndex = (itemIndex + 1) % itemCount

    this.load(context)
  }

  suspend fun load(context: Context): TextWithImageData? {
    val item = items[itemIndex]
    // Falling back to the image already on screen, not to null: this card is redrawn on a
    // five-minute cadence to keep its countdown honest, and a single failed fetch on any
    // one of those passes would otherwise replace the photograph with the placeholder
    // until the network came back.
    val bitmap = fetchImage(context, item.url) ?: data.value?.imageData?.bitmap
    val mappedImageData = ImageData(
      bitmap = bitmap,
      contentDescription = item.imageContentDescription
    )

    // The words come from the app's own generator so the card describes the prayer that
    // is actually current — the canned text said "Fajr" at Asr time. The imagery stays as
    // authored.
    //
    // Same three lines, in the same order, as the "Prayer now" tile in the home
    // carousel: the phase headline, the elapsed reading under it, then the next prayer.
    // The card and the tile are the same content in two places, so a reader moving
    // between them should not have to re-learn the order. See SwipeableBigTiles.kt, which
    // hands these same three strings to InsightPreviewCard.
    val live = com.starception.submission.widget.livePrayerInsight(context)
    if (live != null) lastLiveReadingMillis = System.currentTimeMillis()

    data.value = TextWithImageData(
      textData = TextData(
        key = "$itemIndex",
        primary = live?.title ?: item.primary,
        // The sample's caption slot carries an article's view count; this card's carries
        // how fresh its reading is, which is the equivalent standing detail for content
        // that is a countdown rather than an article.
        caption = live?.let { updatedCaption() } ?: item.caption,
        supporting = live?.elapsed,
        secondary = live?.nextPrayerLine ?: item.secondary
      ),
      imageData = mappedImageData
    )
    return data.value
  }

  /**
   * How fresh the reading is, for the caption slot the sample fills with a view count.
   *
   * A RemoteViews text cannot tick on its own, so this is the age at the moment the card
   * was built, and it stands still until the next widget update — as does the elapsed
   * reading beneath it, which is why the two are shown together. In practice that means
   * "Updated now" on every update, and the older wordings appear only when a build reuses
   * a reading it could not refresh.
   */
  private fun updatedCaption(nowMillis: Long = System.currentTimeMillis()): String {
    val seconds = ((nowMillis - lastLiveReadingMillis) / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
      seconds < 45 -> "Updated now"
      minutes < 2 -> "Updated 1 minute ago"
      minutes < 60 -> "Updated $minutes minutes ago"
      hours < 2 -> "Updated 1 hour ago"
      else -> "Updated $hours hours ago"
    }
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