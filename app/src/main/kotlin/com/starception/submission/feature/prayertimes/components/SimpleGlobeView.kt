package com.starception.submission.feature.prayertimes.components

import android.content.Context as AndroidContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import gov.nasa.worldwind.BasicWorldWindowController
import gov.nasa.worldwind.WorldWind
import gov.nasa.worldwind.WorldWindow
import gov.nasa.worldwind.geom.LookAt
import gov.nasa.worldwind.geom.Position
import gov.nasa.worldwind.layer.BackgroundLayer
import gov.nasa.worldwind.layer.BlueMarbleLandsatLayer
import gov.nasa.worldwind.layer.RenderableLayer
import gov.nasa.worldwind.render.ImageSource
import gov.nasa.worldwind.shape.Placemark
import gov.nasa.worldwind.shape.PlacemarkAttributes

/**
 * Simple Globe View for displaying Qibla direction in the compass
 *
 * A lightweight 3D globe showing:
 * - User's location marker
 * - Kaaba location in Makkah
 * - Optimized camera view showing both locations
 *
 * This is a simplified version without sensor tracking or overlay controls,
 * designed specifically for embedding in the compass component.
 *
 * @param userLatitude User's latitude
 * @param userLongitude User's longitude
 * @param modifier Composable modifier
 */
@Composable
fun SimpleGlobeView(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    // Makkah coordinates (Kaaba)
    val makkahLatitude = 21.4225
    val makkahLongitude = 39.8262

    BoxWithConstraints(modifier = modifier) {
        val tileWidthPx = with(density) { maxWidth.toPx() }
        val tileHeightPx = with(density) { maxHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    val worldWindow = createSimpleWorldWindow(
                        context = ctx,
                        userLat = userLatitude,
                        userLon = userLongitude,
                        makkahLat = makkahLatitude,
                        makkahLon = makkahLongitude
                    )

                    // Enable touch handling
                    worldWindow.isFocusable = true
                    worldWindow.isFocusableInTouchMode = true
                    worldWindow.isClickable = true

                    // Handle touches for pan/zoom
                    worldWindow.setOnTouchListener { v, event ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        worldWindow.worldWindowController?.onTouchEvent(event)
                        true
                    }

                    // Lifecycle management
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> worldWindow.onResume()
                            Lifecycle.Event.ON_PAUSE -> worldWindow.onPause()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)

                    worldWindow.requestFocus()
                    worldWindow
                },
                modifier = Modifier.fillMaxSize(),
                update = { worldWindow ->
                    worldWindow.requestLayout()
                    if (!worldWindow.hasFocus()) {
                        worldWindow.requestFocus()
                    }
                },
                onRelease = { worldWindow ->
                    worldWindow.onPause()
                }
            )
        }
    }
}

/**
 * Create a simple WorldWind globe with user and Kaaba markers
 */
private fun createSimpleWorldWindow(
    context: AndroidContext,
    userLat: Double,
    userLon: Double,
    makkahLat: Double,
    makkahLon: Double
): WorldWindow {
    val worldWindow = WorldWindow(context)

    worldWindow.layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    )

    // Use basic controller
    worldWindow.worldWindowController = BasicWorldWindowController()

    // Add base layers
    worldWindow.layers.addLayer(BackgroundLayer())
    worldWindow.layers.addLayer(BlueMarbleLandsatLayer())

    // Add markers layer
    val markersLayer = RenderableLayer("Markers")
    worldWindow.layers.addLayer(markersLayer)

    // Create Kaaba marker only (no user location - cleaner view)
    val kaabaPos = Position.fromDegrees(makkahLat, makkahLon, 200000.0)

    // Kaaba marker - emoji
    val kaabaBitmap = emojiToBitmapSimple("🕋", 48)
    val kaabaAttributes = PlacemarkAttributes().apply {
        imageSource = ImageSource.fromBitmap(kaabaBitmap)
        imageScale = 0.35
    }
    val kaabaPlacemark = Placemark(kaabaPos, kaabaAttributes).apply {
        altitudeMode = WorldWind.ABSOLUTE
    }
    markersLayer.addRenderable(kaabaPlacemark)

    // Setup camera centered on Kaaba
    val globe = worldWindow.globe
    val earthRadius = globe.equatorialRadius

    // Range to show Earth filling its container
    // Lower value = camera closer = Earth fills more of the view
    val finalRange = earthRadius * 1.8

    val lookAt = LookAt().apply {
        set(
            makkahLat, makkahLon, 0.0,  // Center on Kaaba
            WorldWind.ABSOLUTE,
            finalRange,
            0.0,   // heading - north up
            0.0,   // no tilt - straight on view
            0.0    // roll
        )
    }

    worldWindow.navigator.setAsLookAt(globe, lookAt)

    return worldWindow
}

/**
 * Create a simple blue dot marker for user location
 */
private fun createSimpleUserMarker(): Bitmap {
    val size = 200
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val centerX = size / 2f
    val centerY = size / 2f

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    // White ring
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(centerX, centerY, size * 0.35f, paint)

    // Blue dot
    paint.color = 0xFF4285F4.toInt()
    canvas.drawCircle(centerX, centerY, size * 0.25f, paint)

    return bitmap
}

/**
 * Create bitmap from emoji
 */
private fun emojiToBitmapSimple(emoji: String, sizeDp: Int): Bitmap {
    val size = sizeDp * 3
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        textSize = size * 0.75f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    val textBounds = android.graphics.Rect()
    paint.getTextBounds(emoji, 0, emoji.length, textBounds)
    val y = (size - textBounds.height()) / 2f - textBounds.top

    canvas.drawText(emoji, size / 2f, y, paint)

    return bitmap
}
