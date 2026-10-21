/* Native procedural celestial renderer adapted from celestial-native.
 * Copyright (c) 2026, MIT License.
 */
import CoreImage
import MetalKit
import Shared
import UIKit

private enum CelestialBodyMode: Float { case sun = 1, moon = 2 }

private struct CelestialOptions: Equatable {
    var phase: Float = 0.14
    var body: CelestialBodyMode = .sun
    var playing = true
    var resolutionScale: Float = 1
}

final class CelestialMetalView: MTKView {
    private var celestialRenderer: CelestialRenderer?
    private var options = CelestialOptions()
    private var explicitlyActive = true
    private var applicationActive = UIApplication.shared.applicationState == .active
    private var observers: [NSObjectProtocol] = []

    init() {
        let gpu = MTLCreateSystemDefaultDevice()
        super.init(frame: .zero, device: gpu)
        isOpaque = false
        backgroundColor = .clear
        layer.isOpaque = false
        clearColor = MTLClearColorMake(0, 0, 0, 0)
        colorPixelFormat = .bgra8Unorm
        depthStencilPixelFormat = .invalid
        framebufferOnly = true
        autoResizeDrawable = false
        preferredFramesPerSecond = 30
        enableSetNeedsDisplay = false
        isPaused = true
        isUserInteractionEnabled = false
        do {
            guard let gpu else { throw CelestialError.message("Metal is unavailable.") }
            let renderer = try CelestialRenderer(device: gpu, pixelFormat: colorPixelFormat)
            celestialRenderer = renderer
            delegate = renderer
        } catch {
            assertionFailure(error.localizedDescription)
        }
        let center = NotificationCenter.default
        observers.append(center.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.applicationActive = false
            self?.updateLoop()
        })
        observers.append(center.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.applicationActive = true
            self?.updateLoop()
        })
        observers.append(center.addObserver(
            forName: UIAccessibility.reduceMotionStatusDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in self?.updateLoop() })
    }

    required init(coder: NSCoder) { fatalError("Use CelestialMetalView()") }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    fileprivate func configure(_ value: CelestialOptions) {
        var safe = value
        safe.phase = value.phase.isFinite ? min(1, max(0, value.phase)) : 0.14
        safe.resolutionScale = value.resolutionScale.isFinite
            ? min(1, max(0.5, value.resolutionScale)) : 1
        options = safe
        setNeedsLayout()
        updateLoop()
    }

    override var isHidden: Bool { didSet { updateLoop() } }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        explicitlyActive = window != nil
        updateLoop()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let scale = (window?.screen.scale ?? UIScreen.main.scale) * CGFloat(options.resolutionScale)
        let size = CGSize(width: max(1, bounds.width * scale), height: max(1, bounds.height * scale))
        if drawableSize != size { drawableSize = size }
        if isPaused && window != nil && applicationActive { draw() }
    }

    fileprivate func stop() {
        explicitlyActive = false
        updateLoop()
    }

    private func updateLoop() {
        var effective = options
        effective.playing = options.playing && explicitlyActive && applicationActive &&
            window != nil && !isHidden && !UIAccessibility.isReduceMotionEnabled
        celestialRenderer?.configure(effective)
        isPaused = !effective.playing || effective.body == .moon
        if isPaused && window != nil && applicationActive { draw() }
    }
}

private enum CelestialError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let text): return text }
    }
}

private final class CelestialRenderer: NSObject, MTKViewDelegate {
    private struct Uniforms {
        var viewport: SIMD4<Float>
        var parameters: SIMD4<Float>
    }

    private let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let imageContext: CIContext
    private var options = CelestialOptions()
    private var elapsed: Float = 0
    private var previousTime: CFTimeInterval?

    init(device: MTLDevice, pixelFormat: MTLPixelFormat) throws {
        guard let queue = device.makeCommandQueue(),
              let library = device.makeDefaultLibrary(),
              let vertex = library.makeFunction(name: "celestialVertex"),
              let fragment = library.makeFunction(name: "celestialFragment") else {
            throw CelestialError.message("CelestialShaders.metal is unavailable.")
        }
        self.device = device
        self.queue = queue
        imageContext = CIContext(mtlDevice: device)
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        descriptor.colorAttachments[0].pixelFormat = pixelFormat
        descriptor.colorAttachments[0].isBlendingEnabled = false
        pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        super.init()
    }

    func configure(_ value: CelestialOptions) {
        if value.playing != options.playing { previousTime = nil }
        options = value
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func renderImage(size: Int, options: CelestialOptions, time: Float) -> UIImage? {
        let dimension = max(1, size)
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: dimension,
            height: dimension,
            mipmapped: false
        )
        descriptor.usage = [.renderTarget, .shaderRead]
        guard let texture = device.makeTexture(descriptor: descriptor),
              let command = queue.makeCommandBuffer() else { return nil }
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = texture
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].storeAction = .store
        pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0)
        guard let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return nil }
        var uniforms = Uniforms(
            viewport: SIMD4(Float(dimension), Float(dimension), time, options.phase),
            parameters: SIMD4(options.body.rawValue, 0, 0, 0)
        )
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.commit()
        command.waitUntilCompleted()
        guard let image = CIImage(mtlTexture: texture, options: [.colorSpace: CGColorSpaceCreateDeviceRGB()]),
              let cgImage = imageContext.createCGImage(
                image,
                from: CGRect(x: 0, y: 0, width: dimension, height: dimension)
              ) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    func draw(in view: MTKView) {
        guard view.drawableSize.width > 0, view.drawableSize.height > 0,
              let pass = view.currentRenderPassDescriptor,
              let drawable = view.currentDrawable,
              let command = queue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        let now = CACurrentMediaTime()
        let delta: Float = options.playing
            ? Float(min(0.1, max(0, now - (previousTime ?? now)))) : 0
        previousTime = now
        elapsed += delta
        var uniforms = Uniforms(
            viewport: SIMD4(
                Float(view.drawableSize.width),
                Float(view.drawableSize.height),
                elapsed,
                options.phase
            ),
            parameters: SIMD4(options.body.rawValue, 0, 0, 0)
        )
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<Uniforms>.stride, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }
}

@MainActor
final class CelestialNativeViewProvider: NSObject, @preconcurrency CelestialNativeViewProviding {
    static let shared = CelestialNativeViewProvider()
    private let renderer: CelestialRenderer? = {
        guard let device = MTLCreateSystemDefaultDevice() else { return nil }
        return try? CelestialRenderer(device: device, pixelFormat: .bgra8Unorm)
    }()

    func renderCelestialPng(
        body: Int32,
        lunarCyclePhase: Float,
        pixelSize: Int32,
        timeSeconds: Float
    ) -> Data? {
        renderer?.renderImage(
            size: Int(pixelSize),
            options: CelestialOptions(
                phase: lunarCyclePhase,
                body: body == 2 ? .moon : .sun,
                playing: true
            ),
            time: timeSeconds
        )?.pngData()
    }
}
