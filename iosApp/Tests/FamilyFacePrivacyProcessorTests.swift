import CoreImage
import CoreVideo
import XCTest
import WebRTC

final class FamilyFacePrivacyProcessorTests: XCTestCase {
    private final class Receiver: NSObject, RTCVideoCapturerDelegate {
        let receive: (RTCVideoFrame) -> Void
        init(_ receive: @escaping (RTCVideoFrame) -> Void) { self.receive = receive }
        func capturer(_ capturer: RTCVideoCapturer, didCapture frame: RTCVideoFrame) { receive(frame) }
    }
    private enum DetectionError: Error { case unavailable }
    private func checkerboard(rotation: RTCVideoRotation = ._0, timestampNs: Int64 = 123) throws -> RTCVideoFrame {
        var pixel: CVPixelBuffer?
        let attributes = [kCVPixelBufferIOSurfacePropertiesKey: [:]] as CFDictionary
        XCTAssertEqual(CVPixelBufferCreate(nil, 64, 48, kCVPixelFormatType_32BGRA, attributes, &pixel), kCVReturnSuccess)
        let buffer = try XCTUnwrap(pixel)
        CVPixelBufferLockBaseAddress(buffer, [])
        let bytes = try XCTUnwrap(CVPixelBufferGetBaseAddress(buffer)).assumingMemoryBound(to: UInt8.self)
        let stride = CVPixelBufferGetBytesPerRow(buffer)
        for y in 0..<48 { for x in 0..<64 {
            let value: UInt8 = (x + y) % 2 == 0 ? 0 : 255
            let i = y * stride + x * 4
            bytes[i] = value; bytes[i + 1] = value; bytes[i + 2] = value; bytes[i + 3] = 255
        } }
        CVPixelBufferUnlockBaseAddress(buffer, [])
        return RTCVideoFrame(buffer: RTCCVPixelBuffer(pixelBuffer: buffer), rotation: rotation, timeStampNs: timestampNs)
    }
    private func values(_ frame: RTCVideoFrame) throws -> [UInt8] {
        let pixel = try XCTUnwrap(frame.buffer as? RTCCVPixelBuffer).pixelBuffer
        CVPixelBufferLockBaseAddress(pixel, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixel, .readOnly) }
        let bytes = try XCTUnwrap(CVPixelBufferGetBaseAddress(pixel)).assumingMemoryBound(to: UInt8.self)
        let stride = CVPixelBufferGetBytesPerRow(pixel)
        return (0..<CVPixelBufferGetHeight(pixel)).flatMap { y in
            (0..<CVPixelBufferGetWidth(pixel)).map { x in bytes[y * stride + x * 4] }
        }
    }
    private func protectedResult(detector: @escaping FamilyFacePrivacyProcessor.FaceDetector,
                                 rotation: RTCVideoRotation = ._0, timestampNs: Int64 = 123) throws -> RTCVideoFrame {
        let delivered = expectation(description: "Protected frame")
        var result: RTCVideoFrame?
        let receiver = Receiver { result = $0; delivered.fulfill() }
        let processor = FamilyFacePrivacyProcessor(downstream: receiver, detectFaces: detector,
            context: CIContext(options: [.useSoftwareRenderer: true]))
        defer { processor.close() }
        let capture = RTCVideoCapturer(delegate: processor)
        processor.capturer(capture, didCapture: try checkerboard(rotation: rotation, timestampNs: timestampNs))
        wait(for: [delivered], timeout: 10)
        return try XCTUnwrap(result)
    }

    func testNoFaceDefaultsToWholeFrameBlur() throws {
        let frame = try protectedResult(detector: { _ in [] })
        XCTAssertTrue(try values(frame).allSatisfy { $0 > 40 && $0 < 215 })
        XCTAssertEqual(frame.timeStampNs, 123)
    }
    func testDetectionFailureBlursInsteadOfPassingRawVideo() throws {
        let frame = try protectedResult(detector: { _ in throw DetectionError.unavailable })
        XCTAssertTrue(try values(frame).allSatisfy { $0 > 40 && $0 < 215 })
    }
    func testInvalidFaceBoundsUseWholeFrameFallback() throws {
        let frame = try protectedResult(detector: { _ in [CGRect(x: -100, y: -100, width: 1, height: 1)] })
        XCTAssertTrue(try values(frame).allSatisfy { $0 > 40 && $0 < 215 })
    }
    func testFaceMaskPreservesBackgroundAndTapersAtJaw() throws {
        let frame = try protectedResult(detector: { _ in [CGRect(x: 20, y: 14, width: 24, height: 20)] })
        let pixels = try values(frame)
        XCTAssertEqual(pixels[0], 0)
        XCTAssertEqual(pixels[1], 255)
        XCTAssertEqual(pixels[8 * 64 + 13], 255)
        XCTAssertGreaterThan(pixels[14 * 64 + 46], 60)
        XCTAssertEqual(pixels[33 * 64 + 46], 255)
        XCTAssertTrue(pixels[24 * 64 + 32] > 40 && pixels[24 * 64 + 32] < 215)
    }
    func testDotsPulseOnIdenticalFramesAndLoop() throws {
        let detector: FamilyFacePrivacyProcessor.FaceDetector = { _ in
            [CGRect(x: 20, y: 14, width: 24, height: 20)]
        }
        let start = try values(protectedResult(detector: detector, timestampNs: 0))
        let quarter = try values(protectedResult(detector: detector, timestampNs: 450_000_000))
        let loop = try values(protectedResult(detector: detector, timestampNs: 1_800_000_000))

        XCTAssertNotEqual(start, quarter)
        XCTAssertEqual(start, loop)
        XCTAssertEqual(start[0], quarter[0])
    }

    func testAllCameraRotationsProduceUprightFrames() throws {
        for rotation: RTCVideoRotation in [._0, ._90, ._180, ._270] {
            let frame = try protectedResult(detector: { _ in [] }, rotation: rotation)
            XCTAssertEqual(frame.rotation, ._0)
            XCTAssertEqual(frame.width, rotation == ._90 || rotation == ._270 ? 48 : 64)
            XCTAssertEqual(frame.height, rotation == ._90 || rotation == ._270 ? 64 : 48)
        }
    }
    func testClearVideoRequiresExplicitlyDisablingBlur() throws {
        let original = try checkerboard()
        var delivered = false
        let receiver = Receiver { delivered = true; XCTAssertTrue($0 === original) }
        let processor = FamilyFacePrivacyProcessor(downstream: receiver, detectFaces: { _ in
            XCTFail("Disabled privacy should bypass detection"); return []
        })
        defer { processor.close() }
        processor.setEnabled(false)
        processor.capturer(RTCVideoCapturer(delegate: processor), didCapture: original)
        XCTAssertTrue(delivered)
    }
    func testCloseDropsPendingAndAdditionalFrames() throws {
        try assertPendingFrameDropped(close: true)
    }
    func testPrivacyChangeDiscardsAnInFlightResult() throws {
        try assertPendingFrameDropped(close: false)
    }
    private func assertPendingFrameDropped(close: Bool) throws {
        let started = expectation(description: "Detection started")
        let forbidden = expectation(description: "No stale or raw frame")
        forbidden.isInverted = true
        let unblock = DispatchSemaphore(value: 0)
        let receiver = Receiver { _ in forbidden.fulfill() }
        let processor = FamilyFacePrivacyProcessor(downstream: receiver, detectFaces: { _ in
            started.fulfill()
            _ = unblock.wait(timeout: .now() + 5)
            return []
        }, context: CIContext(options: [.useSoftwareRenderer: true]))
        defer { unblock.signal(); processor.close() }
        let capture = RTCVideoCapturer(delegate: processor)
        processor.capturer(capture, didCapture: try checkerboard())
        wait(for: [started], timeout: 5)
        // The detector is busy: an extra frame must not bypass or queue behind it.
        processor.capturer(capture, didCapture: try checkerboard())
        if close { processor.close() }
        else { processor.setEnabled(false); processor.setEnabled(true) }
        unblock.signal()
        wait(for: [forbidden], timeout: 0.5)
    }
    func testLocalVisionDetectorCanProcessAFrame() throws {
        let frame = try checkerboard()
        let pixel = try XCTUnwrap(frame.buffer as? RTCCVPixelBuffer).pixelBuffer
        XCTAssertTrue(try FamilyFacePrivacyProcessor.localFaces(CIImage(cvPixelBuffer: pixel)).isEmpty)
    }
}
