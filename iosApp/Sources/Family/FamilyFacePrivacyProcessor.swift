import CoreImage
import CoreVideo
import Foundation
import ImageIO
import Vision
import WebRTC

/// Filters the camera before the WebRTC source, protecting both preview and transmitted video.
/// Vision's face detector is supplied by iOS and runs locally; no model download is needed.
final class FamilyFacePrivacyProcessor: NSObject, RTCVideoCapturerDelegate {
    typealias FaceDetector = (CIImage) throws -> [CGRect]
    private let downstream: RTCVideoCapturerDelegate
    private let detectFaces: FaceDetector
    private let context: CIContext
    private let worker = DispatchQueue(label: "FamilyFacePrivacy", qos: .userInitiated)
    private let gate = NSLock()
    private var enabled = true
    private var closed = false
    private var busy = false
    private var revision = 0
    // Only accessed on the worker.
    private var pool: CVPixelBufferPool?
    private var poolSize = CGSize.zero

    init(downstream: RTCVideoCapturerDelegate,
         detectFaces: @escaping FaceDetector = FamilyFacePrivacyProcessor.localFaces,
         context: CIContext = CIContext(options: [.cacheIntermediates: false])) {
        self.downstream = downstream
        self.detectFaces = detectFaces
        self.context = context
        super.init()
    }

    func setEnabled(_ value: Bool) {
        gate.lock(); defer { gate.unlock() }
        if enabled != value { enabled = value; revision += 1 }
    }

    func close() {
        gate.lock(); defer { gate.unlock() }
        closed = true
    }

    func capturer(_ capturer: RTCVideoCapturer, didCapture frame: RTCVideoFrame) {
        gate.lock()
        if closed { gate.unlock(); return }
        if !enabled {
            downstream.capturer(capturer, didCapture: frame)
            gate.unlock()
            return
        }
        // Never queue raw frames behind a slow detector or reuse stale face boxes.
        if busy { gate.unlock(); return }
        busy = true
        let token = revision
        gate.unlock()
        worker.async { [self] in
            defer { gate.lock(); busy = false; gate.unlock() }
            autoreleasepool {
                guard let protected = protectedFrame(frame) else { return }
                gate.lock(); defer { gate.unlock() }
                if !closed && enabled && token == revision {
                    downstream.capturer(capturer, didCapture: protected)
                }
            }
        }
    }

    private func protectedFrame(_ frame: RTCVideoFrame) -> RTCVideoFrame? {
        // The native camera emits CVPixelBuffers. Unsupported formats are dropped, never passed raw.
        guard let input = frame.buffer as? RTCCVPixelBuffer else { return nil }
        let orientation: CGImagePropertyOrientation
        switch frame.rotation {
        case ._90: orientation = .right
        case ._180: orientation = .down
        case ._270: orientation = .left
        default: orientation = .up
        }
        let rotated = CIImage(cvPixelBuffer: input.pixelBuffer).oriented(orientation)
        let image = rotated.transformed(by: CGAffineTransform(translationX: -rotated.extent.minX, y: -rotated.extent.minY))
        let extent = image.extent
        var regions = ((try? detectFaces(image)) ?? []).filter {
            !$0.isNull && !$0.isInfinite && $0.minX.isFinite && $0.minY.isFinite && $0.width.isFinite && $0.height.isFinite
        }.map {
            $0.insetBy(dx: -$0.width / 3, dy: -$0.height / 3).intersection(extent)
        }.filter { !$0.isNull && !$0.isEmpty }
        if regions.isEmpty { regions = [extent] }
        var result = image
        for region in regions {
            let radius = max(12, min(region.width, region.height) / 5)
            let blurred = image.clampedToExtent().applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius])
                .cropped(to: region)
            result = blurred.composited(over: result)
        }
        guard let output = outputBuffer(width: Int(extent.width), height: Int(extent.height)) else { return nil }
        context.render(result, to: output, bounds: extent, colorSpace: CGColorSpaceCreateDeviceRGB())
        return RTCVideoFrame(buffer: RTCCVPixelBuffer(pixelBuffer: output), rotation: ._0, timeStampNs: frame.timeStampNs)
    }

    private func outputBuffer(width: Int, height: Int) -> CVPixelBuffer? {
        let size = CGSize(width: width, height: height)
        if pool == nil || poolSize != size {
            pool = nil
            let attributes: [CFString: Any] = [
                kCVPixelBufferWidthKey: width, kCVPixelBufferHeightKey: height,
                kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_32BGRA,
                kCVPixelBufferIOSurfacePropertiesKey: [:],
                kCVPixelBufferMetalCompatibilityKey: true,
            ]
            guard CVPixelBufferPoolCreate(nil, nil, attributes as CFDictionary, &pool) == kCVReturnSuccess else { return nil }
            poolSize = size
        }
        guard let pool else { return nil }
        var output: CVPixelBuffer?
        let limits = [kCVPixelBufferPoolAllocationThresholdKey: 6] as CFDictionary
        guard CVPixelBufferPoolCreatePixelBufferWithAuxAttributes(nil, pool, limits, &output) == kCVReturnSuccess else { return nil }
        return output
    }

    static func localFaces(_ image: CIImage) throws -> [CGRect] {
        let request = VNDetectFaceRectanglesRequest()
        #if targetEnvironment(simulator)
        // Simulator GPU inference contexts may be unavailable; devices keep hardware acceleration.
        request.usesCPUOnly = true
        #endif
        try VNImageRequestHandler(ciImage: image, orientation: .up).perform([request])
        let size = image.extent.size
        // Vision and Core Image both use a bottom-left origin.
        return (request.results ?? []).map {
            VNImageRectForNormalizedRect($0.boundingBox, Int(size.width), Int(size.height))
        }
    }
}
