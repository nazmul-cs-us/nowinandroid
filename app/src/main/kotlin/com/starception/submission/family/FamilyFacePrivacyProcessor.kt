package com.starception.submission.family

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Protects the actual outgoing track and its local preview, before either sees a camera frame. */
internal class FamilyFacePrivacyProcessor(
    private val downstream: CapturerObserver,
    private val detectFaces: ((ByteArray, Int, Int) -> List<PrivacyRegion>)? = null,
    private val allocate: (Int, Int) -> JavaI420Buffer = JavaI420Buffer::allocate,
) : CapturerObserver {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "FamilyFacePrivacy") }
    private val busy = AtomicBoolean(false)
    private val gate = Any()
    private var enabled = true
    private var closed = false
    private var revision = 0
    // Constructed on the worker: failure to initialize never falls back to sending clear video.
    private val detector by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f).build())
    }
    private var detectorCreated = false
    // Accessed only on the worker. Each mask is based on the current frame's detection.
    private var previousFace: PrivacyRegion? = null
    private var previousTimestampNs = 0L
    private var previousRevision = -1

    fun setEnabled(value: Boolean) = synchronized(gate) {
        if (enabled != value) { revision++; enabled = value }
    }
    override fun onCapturerStarted(success: Boolean) = downstream.onCapturerStarted(success)
    override fun onCapturerStopped() = downstream.onCapturerStopped()

    override fun onFrameCaptured(frame: VideoFrame) {
        synchronized(gate) {
            if (closed) return
            if (!enabled) {
                downstream.onFrameCaptured(frame)
                return
            }
            // No backlog, stale face boxes, or unprocessed frames while detection is busy.
            if (!busy.compareAndSet(false, true)) return
            frame.retain()
            val token = revision
            worker.execute {
                try { protect(frame, token) } finally { frame.release(); busy.set(false) }
            }
        }
    }

    private fun protect(frame: VideoFrame, token: Int) {
        val input = frame.buffer.toI420() ?: return
        var output: JavaI420Buffer? = null
        try {
            val rotated = frame.rotation == 90 || frame.rotation == 270
            val width = if (rotated) input.height else input.width
            val height = if (rotated) input.width else input.height
            output = allocate(width, height)
            val y = upright(input.dataY, input.strideY, input.width, input.height, frame.rotation)
            val u = upright(input.dataU, input.strideU, (input.width + 1) / 2, (input.height + 1) / 2, frame.rotation)
            val v = upright(input.dataV, input.strideV, (input.width + 1) / 2, (input.height + 1) / 2, frame.rotation)
            val chromaWidth = (width + 1) / 2
            val nv21 = ByteArray(y.size + 2 * u.size)
            y.copyInto(nv21)
            for (i in u.indices) { nv21[y.size + 2 * i] = v[i]; nv21[y.size + 2 * i + 1] = u[i] }
            val faces = runCatching {
                detectFaces?.invoke(nv21, width, height) ?: findFaces(nv21, width, height)
            }.getOrDefault(emptyList())
                .filter { region ->
                    val visible = region.clamped(width, height)
                    visible.right > visible.left && visible.bottom > visible.top
                }
            val currentFace = faces.singleOrNull()
            val elapsedNs = frame.timestampNs - previousTimestampNs
            val stabilized = currentFace?.let { face ->
                previousFace?.takeIf { previousRevision == token && elapsedNs in 1L..150_000_000L }
                    ?.let(face::stabilizedFrom) ?: face
            }
            previousFace = currentFace
            previousTimestampNs = frame.timestampNs
            previousRevision = token
            val regions = stabilized?.let { listOf(it) }
                ?: faces.ifEmpty { listOf(PrivacyRegion(0, 0, width, height)) }
            val oval = faces.isNotEmpty()
            for (bounds in regions) {
                val radius = (minOf(bounds.right - bounds.left, bounds.bottom - bounds.top) / 5).coerceAtLeast(12)
                FamilyPrivacyBlur.apply(
                    y, width, height, bounds, radius, oval = oval, dots = oval, timestampNs = frame.timestampNs,
                )
                val chroma = PrivacyRegion(Math.floorDiv(bounds.left, 2), Math.floorDiv(bounds.top, 2),
                    Math.floorDiv(bounds.right + 1, 2), Math.floorDiv(bounds.bottom + 1, 2))
                FamilyPrivacyBlur.apply(u, chromaWidth, (height + 1) / 2, chroma, (radius / 2).coerceAtLeast(1), oval = oval)
                FamilyPrivacyBlur.apply(v, chromaWidth, (height + 1) / 2, chroma, (radius / 2).coerceAtLeast(1), oval = oval)
            }
            write(y, output.dataY, output.strideY, width, height)
            write(u, output.dataU, output.strideU, chromaWidth, (height + 1) / 2)
            write(v, output.dataV, output.strideV, chromaWidth, (height + 1) / 2)
            synchronized(gate) {
                if (!closed && enabled && token == revision) downstream.onFrameCaptured(VideoFrame(output, 0, frame.timestampNs))
            }
        } catch (_: Exception) {
            // Conversion/rendering failure: drop the frame. Never emit the raw input as recovery.
        } finally { output?.release(); input.release() }
    }

    private fun findFaces(nv21: ByteArray, width: Int, height: Int): List<PrivacyRegion> {
        val model = detector.also { detectorCreated = true }
        // Wait on this frame only. Timeouts/errors use whole-frame protection.
        return Tasks.await(model.process(InputImage.fromByteArray(nv21, width, height, 0, InputImage.IMAGE_FORMAT_NV21)),
            500, TimeUnit.MILLISECONDS).map { face ->
            val box = face.boundingBox
            val padX = box.width() / 3
            val padY = box.height() / 3
            PrivacyRegion(box.left - padX, box.top - padY, box.right + padX, box.bottom + padY)
        }
    }

    fun close() = synchronized(gate) {
        if (closed) return@synchronized
        closed = true
        worker.execute { if (detectorCreated) detector.close() }
        worker.shutdown()
    }

    private fun upright(data: ByteBuffer, stride: Int, width: Int, height: Int, rotation: Int): ByteArray {
        val result = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val index = when (rotation) {
                90 -> x * height + height - 1 - y
                180 -> (height - 1 - y) * width + width - 1 - x
                270 -> (width - 1 - x) * height + y
                else -> y * width + x
            }
            result[index] = data.get(y * stride + x)
        }
        return result
    }

    private fun write(data: ByteArray, target: ByteBuffer, stride: Int, width: Int, height: Int) {
        for (y in 0 until height) { target.position(y * stride); target.put(data, y * width, width) }
    }
}
