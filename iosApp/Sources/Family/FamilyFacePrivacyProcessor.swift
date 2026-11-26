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
    private var previousFace: CGRect?
    private var previousTimestampNs: Int64 = 0
    private var previousRevision = -1

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
                guard let protected = protectedFrame(frame, revision: token) else { return }
                gate.lock(); defer { gate.unlock() }
                if !closed && enabled && token == revision {
                    downstream.capturer(capturer, didCapture: protected)
                }
            }
        }
    }

    private func protectedFrame(_ frame: RTCVideoFrame, revision: Int) -> RTCVideoFrame? {
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
        let faces = ((try? detectFaces(image)) ?? []).filter {
            !$0.isNull && !$0.isInfinite && $0.minX.isFinite && $0.minY.isFinite && $0.width.isFinite
                && $0.height.isFinite && !$0.isEmpty
        }.map {
            $0.insetBy(dx: -$0.width / 3, dy: -$0.height / 3)
        }.filter { $0.intersects(extent) && !$0.intersection(extent).isEmpty }
        let currentFace = faces.count == 1 ? faces.first : nil
        let elapsedNs = frame.timeStampNs - previousTimestampNs
        let regions: [CGRect]
        if let currentFace, let previousFace, previousRevision == revision, (1...150_000_000).contains(elapsedNs) {
            let xLimit = currentFace.width * 0.025
            let yLimit = currentFace.height * 0.025
            let shiftX = min(xLimit, max(-xLimit, (previousFace.midX - currentFace.midX) * 0.5))
            let shiftY = min(yLimit, max(-yLimit, (previousFace.midY - currentFace.midY) * 0.5))
            // Expand the shifted mask to preserve coverage around the current face.
            regions = [currentFace.insetBy(dx: -abs(shiftX), dy: -abs(shiftY)).offsetBy(dx: shiftX, dy: shiftY)]
        } else {
            regions = faces.isEmpty ? [extent] : faces
        }
        previousFace = currentFace
        previousTimestampNs = frame.timeStampNs
        previousRevision = revision
        let cycleNs: Int64 = 2_800_000_000
        let phase = CGFloat(frame.timeStampNs % cycleNs) / CGFloat(cycleNs)
        let pulse = 0.055 * sin(2 * CGFloat.pi * phase)
        var result = image
        for region in regions {
            let radius = max(12, min(region.width, region.height) / 5)
            let visible = region.intersection(extent)
            let blurred = image.clampedToExtent().applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: radius])
                .cropped(to: visible)
            if faces.isEmpty {
                result = blurred
            } else {
                let faceCenter = CIVector(x: region.midX, y: region.midY)
                let faceRadii = CIVector(x: region.width / 2, y: region.height / 2)
                let mask = Self.faceShapeMask?.apply(extent: visible, arguments: [faceCenter, faceRadii])
                    ?? radialMask(center: CIVector(x: 0, y: 0), solidRadius: 0.87, outerRadius: 1)
                    .transformed(by: CGAffineTransform(a: region.width / 2, b: 0, c: 0,
                        d: region.height / 2, tx: region.midX, ty: region.midY))
                    .cropped(to: visible)
                let spacing = min(14, max(5, min(region.width, region.height) / 18))
                let dots = Self.faceDotMask?.apply(extent: visible, arguments: [
                    faceCenter,
                    faceRadii,
                    // One cell of drift per cycle keeps the grid seamless.
                    CIVector(x: region.minX + spacing * phase, y: region.minY - spacing * phase),
                    spacing,
                    pulse,
                ])
                let textured: CIImage
                if let dots {
                    let highlight = CIImage(color: CIColor(red: 0.96, green: 0.98, blue: 1, alpha: 0.36))
                        .cropped(to: visible).composited(over: blurred)
                    textured = highlight.applyingFilter("CIBlendWithMask", parameters: [
                        kCIInputBackgroundImageKey: blurred, kCIInputMaskImageKey: dots,
                    ])
                } else {
                    textured = blurred
                }
                let clear = CIImage(color: CIColor(red: 0, green: 0, blue: 0, alpha: 0)).cropped(to: visible)
                let masked = textured.applyingFilter("CIBlendWithMask", parameters: [
                    kCIInputBackgroundImageKey: clear, kCIInputMaskImageKey: mask,
                ]).cropped(to: visible)
                result = masked.composited(over: result)
            }
        }
        guard let output = outputBuffer(width: Int(extent.width), height: Int(extent.height)) else { return nil }
        context.render(result, to: output, bounds: extent, colorSpace: CGColorSpaceCreateDeviceRGB())
        return RTCVideoFrame(buffer: RTCCVPixelBuffer(pixelBuffer: output), rotation: ._0, timeStampNs: frame.timeStampNs)
    }

    private static let faceShapeMask = CIColorKernel(source: """
        kernel vec4 faceShapeMask(vec2 faceCenter, vec2 faceRadii) {
            vec2 position = (destCoord() - faceCenter) / faceRadii;
            float y = -position.y;
            float jawTaper = 1.0 - 0.36 * clamp((y - 0.20) / 0.80, 0.0, 1.0);
            float x = position.x / jawTaper;
            float ySquared = y * y;
            float mask = clamp((1.0 - x * x - ySquared * ySquared) / 0.24, 0.0, 1.0);
            return vec4(mask, mask, mask, 1.0);
        }
        """)

    // Use each grid cell's center to give its dot one size and one elliptical outline.
    private static let faceDotMask = CIColorKernel(source: """
        kernel vec4 faceDotMask(vec2 faceCenter, vec2 faceRadii, vec2 gridOrigin, float spacing, float pulse) {
            vec2 pixel = destCoord();
            vec2 offset = mod(pixel - gridOrigin, spacing) - spacing * 0.5;
            vec2 position = (pixel - offset - faceCenter) / faceRadii;
            float radius = spacing * (0.34 - 0.13 * min(dot(position, position), 1.0) + pulse);
            vec2 scale = 1.0 - 0.42 * min(abs(position), vec2(1.0));
            vec2 ellipse = offset / (radius * scale);
            float mask = clamp((1.0 - dot(ellipse, ellipse)) * 2.6, 0.0, 1.0);
            return vec4(mask, mask, mask, 1.0);
        }
        """)

    private func radialMask(center: CIVector, solidRadius: CGFloat, outerRadius: CGFloat) -> CIImage {
        let gradient = CIFilter(name: "CIRadialGradient", parameters: [
            kCIInputCenterKey: center,
            "inputRadius0": solidRadius,
            "inputRadius1": outerRadius,
            "inputColor0": CIColor(red: 1, green: 1, blue: 1),
            "inputColor1": CIColor(red: 0, green: 0, blue: 0),
        ])
        // An unavailable mask must cover the region rather than reveal the face.
        return gradient?.outputImage ?? CIImage(color: CIColor(red: 1, green: 1, blue: 1))
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
