package com.starception.submission.feature.prayertimes.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
 * WorldWind 3D globe for the Qibla compass.
 *
 * Layout: FrameLayout (explicit square px size)
 *   ├── WorldWindow (GLSurfaceView) — clipped to circle via ViewOutlineProvider
 *   └── RingOverlayView            — Android View drawn above GL surface
 *
 * Fixes:
 *   • Perfect circle  — ViewOutlineProvider on WorldWindow clips at the View level.
 *                       FrameLayout given explicit square px size (not MATCH_PARENT)
 *                       so WorldWind's GL viewport is always 1:1 aspect ratio.
 *   • Ring outside    — RingOverlayView sits above GLSurfaceView in the same FrameLayout;
 *                       OS compositor always places it on top of the GL overlay.
 *   • No ANR          — navigator set via queueEvent on the GL thread; AndroidView
 *                       inflated after 32 ms frame delay.
 *   • No black space  — range = earthRadius × 1.05 makes Earth slightly overfill the
 *                       square GL viewport so the circle clip always hits ocean/land.
 */
@Composable
fun SimpleGlobeView(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier,
    ringStrokeWidthPx: Float = 12f,
    ringColor:         Int   = 0xFFFFFFFF.toInt(),
    ringTintColor:     Int   = 0x4D10B981,
    arcColor:          Int   = 0xFF10B981.toInt(),
    arcStartAngleDeg:  Float = -90f,
    arcSweepDeg:       Float = 54f,
    arcRotationDeg:    Float = 0f,
    showArc:           Boolean = true,
    deviceHeadingDeg:  Float = 0f,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density        = LocalDensity.current

    val makkahLatitude  = 21.4225
    val makkahLongitude = 39.8262

    // Stable reference to the overlay so we can push arc updates without recreating views
    val overlayRef = remember { mutableStateOf<RingOverlayView?>(null) }

    // Pre-compute the camera heading (user→Kaaba azimuth) so the overlay knows
    // where the user dot sits relative to center.
    // The camera looks at the midpoint with this heading, so the user dot
    // is offset downward (opposite of heading) from center on screen.
    val cameraHeadingDeg = remember(userLatitude, userLongitude) {
        val userPos = Position.fromDegrees(userLatitude, userLongitude, 0.0)
        val kaabaPos = Position.fromDegrees(makkahLatitude, makkahLongitude, 0.0)
        userPos.greatCircleAzimuth(kaabaPos).toFloat()
    }

    // Push updated arc params into the overlay on recomposition
    LaunchedEffect(arcRotationDeg, arcSweepDeg, arcColor, ringTintColor, showArc, deviceHeadingDeg) {
        overlayRef.value?.apply {
            this.arcRotationDeg    = arcRotationDeg
            this.arcSweepDeg       = arcSweepDeg
            this.arcColor          = arcColor
            this.showArc           = showArc
            this.deviceHeadingDeg  = deviceHeadingDeg
            this.cameraHeadingDeg  = cameraHeadingDeg
            invalidate()
        }
    }

    // Defer AndroidView inflation by one frame so the Compose layout pass finishes
    // before GL surface creation starts — prevents main-thread stall on emulator.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(32)
        ready = true
    }

    // BoxWithConstraints gives us the actual measured pixel size so we can force
    // the FrameLayout and WorldWindow to an exact square — MATCH_PARENT inherits
    // Compose constraints which may be non-square, making WorldWind render oval.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sidePx = with(density) { minOf(maxWidth, maxHeight).roundToPx() }

        if (ready && sidePx > 0) {
            AndroidView(
                factory = { ctx ->
                    // ── FrameLayout: explicit square size ──────────────────
                    val frame = FrameLayout(ctx)
                    val lp    = ViewGroup.LayoutParams(sidePx, sidePx)

                    // ── WorldWindow (GLSurfaceView) ────────────────────────
                    val ww = WorldWindow(ctx)
                    ww.worldWindowController = BasicWorldWindowController()
                    ww.layers.addLayer(BackgroundLayer())
                    ww.layers.addLayer(BlueMarbleLandsatLayer())

                    val markersLayer = RenderableLayer("Markers")
                    ww.layers.addLayer(markersLayer)

                    val userPos  = Position.fromDegrees(userLatitude,  userLongitude,  0.0)
                    val kaabaPos = Position.fromDegrees(makkahLatitude, makkahLongitude, 0.0)

                    // User dot is drawn in the overlay (RingOverlayView) for exact
                    // alignment with the radar direction cone.
                    markersLayer.addRenderable(
                        Placemark(kaabaPos, PlacemarkAttributes().apply {
                            imageSource = ImageSource.fromBitmap(emojiToBitmap("🕋", 48))
                            imageScale  = 0.38
                        }).apply { altitudeMode = WorldWind.ABSOLUTE }
                    )

                    // Inset the globe by the ring stroke width so the ring sits outside the globe
                    val inset = ringStrokeWidthPx.toInt()
                    val globeSizePx = sidePx - inset * 2

                    // Clip GLSurfaceView to a perfect circle at the View level.
                    ww.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                    ww.clipToOutline = true
                    val wwLp = FrameLayout.LayoutParams(globeSizePx, globeSizePx).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    frame.addView(ww, wwLp)

                    // ── Ring + arc overlay ─────────────────────────────────
                    val overlay = RingOverlayView(ctx).apply {
                        this.ringStrokeWidthPx  = ringStrokeWidthPx
                        this.ringColor          = ringColor
                        this.ringTintColor      = ringTintColor
                        this.arcColor           = arcColor
                        this.arcStartAngleDeg   = arcStartAngleDeg
                        this.arcSweepDeg        = arcSweepDeg
                        this.arcRotationDeg     = arcRotationDeg
                        this.showArc            = showArc
                        this.deviceHeadingDeg   = deviceHeadingDeg
                        this.cameraHeadingDeg   = cameraHeadingDeg
                        this.userLatitude       = userLatitude
                        this.userLongitude      = userLongitude
                        this.globeViewportHalf  = globeSizePx / 2f
                    }
                    overlayRef.value = overlay
                    frame.addView(overlay, ViewGroup.LayoutParams(sidePx, sidePx))

                    // ── Camera on GL thread (no ANR) ───────────────────────
                    ww.queueEvent {
                        val globe   = ww.globe
                        val heading = userPos.greatCircleAzimuth(kaabaPos)
                        val midLat  = (userLatitude  + makkahLatitude)  / 2.0
                        val midLon  = (userLongitude + makkahLongitude) / 2.0
                        val range   = globe.equatorialRadius * 1.05
                        ww.navigator.setAsLookAt(globe, LookAt().apply {
                            set(midLat, midLon, 0.0, WorldWind.ABSOLUTE, range, heading, 0.0, 0.0)
                        })
                        ww.requestRedraw()
                    }

                    // ── Lifecycle ──────────────────────────────────────────
                    lifecycleOwner.lifecycle.addObserver(
                        LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> ww.onResume()
                                Lifecycle.Event.ON_PAUSE  -> ww.onPause()
                                else -> {}
                            }
                        }
                    )

                    frame.layoutParams = lp
                    frame
                },
                modifier = Modifier.size(
                    with(density) { sidePx.toDp() },
                    with(density) { sidePx.toDp() }
                ),
                update = { _ ->
                    overlayRef.value?.let { ov ->
                        ov.arcRotationDeg   = arcRotationDeg
                        ov.arcSweepDeg      = arcSweepDeg
                        ov.arcColor         = arcColor
                        ov.showArc          = showArc
                        ov.deviceHeadingDeg = deviceHeadingDeg
                        ov.cameraHeadingDeg = cameraHeadingDeg
                        ov.invalidate()
                    }
                },
                onRelease = { frame ->
                    (frame.getChildAt(0) as? WorldWindow)?.onPause()
                    overlayRef.value = null
                }
            )
        }
    }
}

/**
 * Android View that draws the ring and rotating arc ABOVE the GLSurfaceView.
 * Lives in the same FrameLayout so the OS compositor always stacks it on top.
 */
class RingOverlayView(ctx: Context) : View(ctx) {
    var ringStrokeWidthPx: Float   = 12f
    var ringColor:         Int     = 0xFFFFFFFF.toInt()
    var ringTintColor:     Int     = 0x4D10B981
    var arcColor:          Int     = 0xFF10B981.toInt()
    var arcStartAngleDeg:  Float   = -90f
    var arcSweepDeg:       Float   = 54f
    var arcRotationDeg:    Float   = 0f
    var showArc:           Boolean = true
    var deviceHeadingDeg:  Float   = 0f
    var cameraHeadingDeg:  Float   = 0f
    var userLatitude:      Double  = 0.0
    var userLongitude:     Double  = 0.0
    var makkahLatitude:    Double  = 21.4225
    var makkahLongitude:   Double  = 39.8262
    var globeViewportHalf: Float   = 0f  // half of the WorldWindow viewport size in pixels

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val oval = android.graphics.RectF()
    private val radarOval = android.graphics.RectF()
    private val radarPath = android.graphics.Path()

    override fun onDraw(canvas: Canvas) {
        val sw     = ringStrokeWidthPx
        val radius = (minOf(width, height) / 2f) - sw / 2f - 1f
        val cx     = width  / 2f
        val cy     = height / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // --- Radar direction cone from user location (Google Maps style) ---
        // The globe (WorldWindow) is inset by ringStrokeWidthPx on each side.
        val globeRadius = (minOf(width, height) / 2f) - sw - 1f

        // ── Geometric user dot screen position ──
        // Camera looks at midpoint(user, Kaaba) with tilt=0, heading=azimuth(user→Kaaba).
        // On screen: heading direction points UP, user dot is directly below center
        // at a distance proportional to half the great-circle angular distance.
        //
        // Haversine for angular distance:
        val lat1 = Math.toRadians(userLatitude)
        val lat2 = Math.toRadians(makkahLatitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(makkahLongitude - userLongitude)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2).let { it * it }
        val angularDistRad = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val halfAngDist = angularDistRad / 2.0

        // With range=1.05*R and FOV=45°, the sphere-to-viewport projection is:
        //   NDC = sin(halfAngDist) * focalLen / (1.05 + cos(halfAngDist))
        // where focalLen = 1/tan(FOV/2) = 1/tan(22.5°) ≈ 2.414
        val focalLen = 1.0 / Math.tan(Math.toRadians(22.5))
        val ndc = Math.sin(halfAngDist) * focalLen / (1.05 + Math.cos(halfAngDist))

        // Map NDC to pixels (viewport half = globeSizePx/2)
        val vpHalf = if (globeViewportHalf > 0f) globeViewportHalf else globeRadius
        val offsetPx = (ndc * vpHalf).toFloat()

        // User dot is offset from center DOWNWARD on screen (opposite of heading direction).
        // On canvas: heading direction = "up" = -90° in canvas convention.
        // So user dot is at canvas angle = +90° from center (straight down).
        val userDotX = cx
        val userDotY = cy + offsetPx

        val radarRadius = globeRadius * 0.75f
        val coneSweep = 65f // wide, dominant cone
        // On screen, camera heading points "up" (negative Y direction).
        // Device heading is CW from magnetic north.
        // Canvas angles: 0° = right (3 o'clock), 90° = down, -90° = up.
        // Difference (deviceHeading - cameraHeading) = rotation from screen-up.
        // Canvas angle = difference - 90° to convert compass→canvas convention.
        val screenHeading = (deviceHeadingDeg - cameraHeadingDeg) - 90f
        val coneStart = screenHeading - coneSweep / 2f

        radarOval.set(
            userDotX - radarRadius, userDotY - radarRadius,
            userDotX + radarRadius, userDotY + radarRadius
        )

        // Clip radar cone to the globe circle so it doesn't bleed into the ring
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply {
            addCircle(cx, cy, globeRadius, android.graphics.Path.Direction.CW)
        })

        // Bright orange/amber cone — stands out against blue ocean on globe
        val coneColor = 0xFFFF9800.toInt()  // Material Orange 500
        val shader = android.graphics.RadialGradient(
            userDotX, userDotY, radarRadius,
            intArrayOf(0xFFFF9800.toInt(), 0xCCFF9800.toInt(), 0x66FF9800.toInt(), 0x00FF9800.toInt()),
            floatArrayOf(0f, 0.2f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        radarPaint.shader = shader
        radarPath.reset()
        radarPath.moveTo(userDotX, userDotY)
        radarPath.arcTo(radarOval, coneStart, coneSweep)
        radarPath.close()
        canvas.drawPath(radarPath, radarPaint)
        radarPaint.shader = null

        // Google Maps style user dot: white border ring + blue fill
        val dotRadius = minOf(width, height) / 38f  // scale with view size
        radarPaint.color = 0xFFFFFFFF.toInt()
        radarPaint.style = android.graphics.Paint.Style.FILL
        canvas.drawCircle(userDotX, userDotY, dotRadius + 2f, radarPaint)
        radarPaint.color = 0xFF4285F4.toInt()
        canvas.drawCircle(userDotX, userDotY, dotRadius, radarPaint)

        canvas.restore()

        paint.strokeWidth = sw

        // White base track
        paint.color = ringColor
        canvas.drawCircle(cx, cy, radius, paint)

        // Colour tint
        paint.color = ringTintColor
        canvas.drawCircle(cx, cy, radius, paint)

        // Rotating arc
        if (showArc) {
            canvas.save()
            canvas.rotate(arcRotationDeg, cx, cy)
            paint.color = arcColor
            canvas.drawArc(oval, arcStartAngleDeg, arcSweepDeg, false, paint)
            canvas.restore()
        }
    }
}

private fun emojiToBitmap(emoji: String, sizeDp: Int): Bitmap {
    val size  = sizeDp * 3
    val bmp   = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv    = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = size * 0.72f
        textAlign = Paint.Align.CENTER
    }
    val b = android.graphics.Rect()
    paint.getTextBounds(emoji, 0, emoji.length, b)
    cv.drawText(emoji, size/2f, (size - b.height())/2f - b.top, paint)
    return bmp
}
