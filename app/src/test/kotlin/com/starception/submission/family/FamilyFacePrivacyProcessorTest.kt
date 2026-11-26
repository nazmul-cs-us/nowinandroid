package com.starception.submission.family

import org.junit.Assert.*
import org.junit.Test
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class FamilyFacePrivacyProcessorTest {
    private class Receiver : CapturerObserver {
        val delivered = CountDownLatch(1)
        val timestamps = LinkedBlockingQueue<Long>()
        var pixels = byteArrayOf()
        var rotation = -1
        var width = 0
        var height = 0
        override fun onCapturerStarted(success: Boolean) = Unit
        override fun onCapturerStopped() = Unit
        override fun onFrameCaptured(frame: VideoFrame) {
            val buffer = frame.buffer.toI420()!!
            try {
                width = buffer.width; height = buffer.height; rotation = frame.rotation
                pixels = ByteArray(width * height) { i -> buffer.dataY.get(i / width * buffer.strideY + i % width) }
            } finally { buffer.release() }
            timestamps.add(frame.timestampNs)
            delivered.countDown()
        }
        fun await() { assertTrue("Expected a protected frame", delivered.await(5, TimeUnit.SECONDS)) }
    }

    private fun allocate(width: Int, height: Int): JavaI420Buffer {
        val cw = (width + 1) / 2
        val ch = (height + 1) / 2
        return JavaI420Buffer.wrap(width, height, ByteBuffer.allocateDirect(width * height), width,
            ByteBuffer.allocateDirect(cw * ch), cw, ByteBuffer.allocateDirect(cw * ch), cw, null)
    }
    private fun frame(rotation: Int = 0, timestampNs: Long = 123L): VideoFrame {
        val buffer = allocate(32, 24)
        for (i in 0 until 32 * 24) buffer.dataY.put(i, if ((i + i / 32) % 2 == 0) 0 else 255.toByte())
        return VideoFrame(buffer, rotation, timestampNs)
    }
    private fun submit(processor: FamilyFacePrivacyProcessor, frame: VideoFrame = frame()) {
        try { processor.onFrameCaptured(frame) } finally { frame.release() }
    }

    @Test fun noFacesBlursTheWholeFrameByDefault() {
        val receiver = Receiver()
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ -> emptyList() }, ::allocate)
        try {
            submit(processor); receiver.await()
            assertTrue(receiver.pixels.all { (it.toInt() and 255) in 110..145 })
        } finally { processor.close() }
    }

    @Test fun briefDetectionLossHoldsTheProtectedFrameBeforeFullFrameFallback() {
        val receiver = Receiver()
        val detected = LinkedBlockingQueue<Unit>()
        val faceVisible = AtomicBoolean(true)
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ ->
            val faces = if (faceVisible.get()) listOf(PrivacyRegion(8, 4, 24, 20)) else emptyList()
            detected.add(Unit)
            faces
        }, ::allocate)
        fun submitAndAwaitDetection(timestampNs: Long) {
            repeat(100) {
                submit(processor, frame(timestampNs = timestampNs))
                if (detected.poll(20, TimeUnit.MILLISECONDS) != null) return
            }
            fail("Expected a frame to reach face detection")
        }

        try {
            submitAndAwaitDetection(1)
            assertEquals(1L, receiver.timestamps.poll(5, TimeUnit.SECONDS))
            assertEquals(0, receiver.pixels.first().toInt())

            faceVisible.set(false)
            submitAndAwaitDetection(50_000_001)
            assertNull(receiver.timestamps.poll(150, TimeUnit.MILLISECONDS))

            faceVisible.set(true)
            submitAndAwaitDetection(100_000_001)
            assertEquals(100_000_001L, receiver.timestamps.poll(5, TimeUnit.SECONDS))
            assertEquals(0, receiver.pixels.first().toInt())

            faceVisible.set(false)
            submitAndAwaitDetection(350_000_001)
            assertEquals(350_000_001L, receiver.timestamps.poll(5, TimeUnit.SECONDS))
            assertTrue(receiver.pixels.all { (it.toInt() and 255) in 110..145 })
        } finally { processor.close() }
    }

    @Test fun detectorFailureAlsoBlursTheWholeFrame() {
        val receiver = Receiver()
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ -> error("Model unavailable") }, ::allocate)
        try {
            submit(processor); receiver.await()
            assertTrue(receiver.pixels.all { (it.toInt() and 255) in 110..145 })
        } finally { processor.close() }
    }

    @Test fun invalidFaceBoundsCannotProduceAClearFrame() {
        val receiver = Receiver()
        val processor = FamilyFacePrivacyProcessor(receiver,
            { _, _, _ -> listOf(PrivacyRegion(-20, -20, -10, -10)) }, ::allocate)
        try {
            submit(processor); receiver.await()
            assertTrue(receiver.pixels.all { (it.toInt() and 255) in 110..145 })
        } finally { processor.close() }
    }

    @Test fun renderingFailureDropsTheFrameInsteadOfSendingTheInput() {
        val receiver = Receiver()
        val attempted = CountDownLatch(1)
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ -> emptyList() }, { _, _ ->
            attempted.countDown(); error("Could not allocate output")
        })
        try {
            submit(processor)
            assertTrue(attempted.await(5, TimeUnit.SECONDS))
            assertFalse(receiver.delivered.await(200, TimeUnit.MILLISECONDS))
        } finally { processor.close() }
    }

    @Test fun clearFramesRequireExplicitlyDisablingPrivacy() {
        val receiver = Receiver()
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ -> error("Must not detect") }, ::allocate)
        try {
            processor.setEnabled(false)
            submit(processor); receiver.await()
            assertEquals(setOf(0, 255), receiver.pixels.map { it.toInt() and 255 }.toSet())
        } finally { processor.close() }
    }

    @Test fun detectionUsesUprightPixelsForEveryCameraRotation() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val receiver = Receiver()
            val processor = FamilyFacePrivacyProcessor(receiver, { _, w, h ->
                assertEquals(if (rotation % 180 == 0) 32 else 24, w)
                assertEquals(if (rotation % 180 == 0) 24 else 32, h)
                emptyList()
            }, ::allocate)
            try {
                submit(processor, frame(rotation)); receiver.await()
                assertEquals(0, receiver.rotation)
                assertEquals(if (rotation % 180 == 0) 32 else 24, receiver.width)
                assertEquals(if (rotation % 180 == 0) 24 else 32, receiver.height)
            } finally { processor.close() }
        }
    }

    @Test fun busyDetectionDropsExtraFramesAndCloseDiscardsThePendingFrame() {
        val receiver = Receiver()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var detections = 0
        val processor = FamilyFacePrivacyProcessor(receiver, { _, _, _ ->
            detections++; entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); emptyList()
        }, ::allocate)
        try {
            submit(processor)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            submit(processor)
            processor.close()
            release.countDown()
            assertFalse(receiver.delivered.await(200, TimeUnit.MILLISECONDS))
            assertEquals(1, detections)
            submit(processor)
            assertFalse(receiver.delivered.await(100, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); processor.close() }
    }
}
