//
//  SalahScene3DHost.swift
//  iosApp
//
//  The Swift3D-backed salah posture scene — the iOS counterpart of Android's
//  SceneView visualization. Kotlin pushes mode + frame data through the
//  Salah3DSceneService protocol; this host renders it with Metal.
//

import SwiftUI
import Swift3D
import Shared
import simd

// MARK: - Posture palette (Android parity, SalahPosture.classificationLabels order)

private let salahPostureColors: [Color] = [
    Color(red: 0x48 / 255.0, green: 0xD9 / 255.0, blue: 0xFF / 255.0),  // QIYAM
    Color(red: 0xFF / 255.0, green: 0xB3 / 255.0, blue: 0x47 / 255.0),  // RUKU
    Color(red: 0xFF / 255.0, green: 0x4F / 255.0, blue: 0xA3 / 255.0),  // GOING_TO_SUJUD
    Color(red: 0x72 / 255.0, green: 0xED / 255.0, blue: 0x7D / 255.0),  // SUJUD
    Color(red: 0xAD / 255.0, green: 0x8C / 255.0, blue: 0xFF / 255.0),  // JALSA
    Color(red: 0xFF / 255.0, green: 0x70 / 255.0, blue: 0x4D / 255.0),  // TASHAHHUD
    Color(red: 0x42 / 255.0, green: 0xF5 / 255.0, blue: 0xD4 / 255.0),  // QIYAM_RISING
    Color(red: 0x16 / 255.0, green: 0xC7 / 255.0, blue: 0x9A / 255.0),  // RISING_TO_QIYAM
    Color(red: 0x9A / 255.0, green: 0xA5 / 255.0, blue: 0xB1 / 255.0),  // NOT_PRAYING
]

private func salahPostureColor(_ index: Int) -> Color {
    salahPostureColors.indices.contains(index) ? salahPostureColors[index] : .white
}

private let gravityTeal = Color(red: 0.06, green: 0.72, blue: 0.55)
private let gravityTip = Color(red: 0.95, green: 0.75, blue: 0.2)

// MARK: - Scene state (Kotlin writes, SwiftUI observes)

final class SalahSceneState: ObservableObject {
    @Published var mode: Int = 0
    @Published var joints: [simd_float3] = []
    @Published var postureIndex: Int = 0
    @Published var points: [simd_float3] = []
    @Published var pointPostures: [Int] = []
    @Published var playhead: simd_float3? = nil
    @Published var gravity: simd_float3 = simd_float3(0, 0, 0)
    @Published var camYaw: Float = 0.6
    @Published var camPitch: Float = 0.35

    /// The pose Kotlin last pushed; the displayed [joints] chase it at the
    /// display rate so keyframe snaps ease smoothly without Swift3D node
    /// transitions (which fight high-frequency pushes and cause jitter).
    private var targetJoints: [simd_float3] = []
    private var displayTimer: Timer?

    init() {
        displayTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
            self?.advancePose()
        }
    }

    deinit {
        displayTimer?.invalidate()
    }

    var poseTarget: [simd_float3] {
        get { targetJoints }
        set { targetJoints = newValue }
    }

    private func advancePose() {
        guard !targetJoints.isEmpty else { return }
        if joints.count != targetJoints.count {
            joints = targetJoints
            return
        }
        var next = joints
        var settled = true
        for index in next.indices {
            let delta = targetJoints[index] - next[index]
            if simd_length_squared(delta) > 0.00001 { settled = false }
            next[index] = next[index] + delta * 0.35
        }
        if settled {
            if joints != targetJoints { joints = targetJoints }
            return
        }
        joints = next
    }
}

// MARK: - Geometry helpers

/// A bone between two joints: a capsule aligned from a → b.
/// The capsule mesh spans y ∈ [-1.5, 1.5] (height 3, radius 0.5), so the
/// scale maps the bone length (plus its round caps) onto it.
private func salahBone(id: String, from: simd_float3, to: simd_float3,
                       radius: Float, color: Color) -> some Node {
    let delta = to - from
    let length = simd_length(delta)
    let direction = length > 0.0001 ? delta / length : simd_float3(0, 1, 0)
    let mid = (from + to) * 0.5
    let up = simd_float3(0, 1, 0)
    let rotation: simd_quatf
    if abs(simd_dot(direction, up)) > 0.9995 {
        rotation = simd_quatf(angle: direction.y >= 0 ? 0 : Float.pi,
                              axis: simd_float3(0, 0, 1))
    } else {
        let axis = simd_normalize(simd_cross(up, direction))
        let cosine = max(-1, min(1, simd_dot(up, direction)))
        rotation = simd_quatf(angle: acos(cosine), axis: axis)
    }
    let scaleY = max(length + radius * 2, 0.02) / 3
    return CapsuleNode(id: id)
        .transform(float4x4.TRS(
            trans: mid,
            rot: rotation,
            scale: simd_float3(radius * 2, scaleY, radius * 2),
        ))
        .shaded(.unlit(color))
}

private func salahSphere(id: String, position: simd_float3,
                         scale: Float, color: Color) -> some Node {
    SphereNode(id: id)
        .transform(float4x4.translated(position))
        .scaled(simd_float3(scale, scale, scale))
        .shaded(.unlit(color))
}

// MARK: - Scene pieces

/// The 16-joint skeleton posed by the pushed keyframe. Joint order matches
/// Kotlin's enum: HEAD, NECK, L/R_SHOULDER, L/R_ELBOW, L/R_WRIST, L/R_HIP,
/// L/R_KNEE, L/R_ANKLE, L/R_TOE.
private struct HumanoidFigure: Node {
    var id: String { "humanoid" }
    let joints: [simd_float3]
    let color: Color

    private struct BoneSpec: Identifiable {
        let id: String
        let a: Int
        let b: Int
        let radius: Float
    }

    /// Joint order matches Kotlin's enum: HEAD, NECK, L/R_SHOULDER, L/R_ELBOW,
    /// L/R_WRIST, L/R_HIP, L/R_KNEE, L/R_ANKLE, L/R_TOE.
    private static let bones: [BoneSpec] = [
        BoneSpec(id: "neck", a: 1, b: 0, radius: 0.030),
        BoneSpec(id: "shoulders", a: 2, b: 3, radius: 0.050),
        BoneSpec(id: "hips", a: 8, b: 9, radius: 0.045),
        BoneSpec(id: "l-upper-arm", a: 2, b: 4, radius: 0.032),
        BoneSpec(id: "l-forearm", a: 4, b: 6, radius: 0.026),
        BoneSpec(id: "r-upper-arm", a: 3, b: 5, radius: 0.032),
        BoneSpec(id: "r-forearm", a: 5, b: 7, radius: 0.026),
        BoneSpec(id: "l-thigh", a: 8, b: 10, radius: 0.036),
        BoneSpec(id: "l-shin", a: 10, b: 12, radius: 0.030),
        BoneSpec(id: "l-foot", a: 12, b: 14, radius: 0.020),
        BoneSpec(id: "r-thigh", a: 9, b: 11, radius: 0.036),
        BoneSpec(id: "r-shin", a: 11, b: 13, radius: 0.030),
        BoneSpec(id: "r-foot", a: 13, b: 15, radius: 0.020),
    ]

    private static let jointDots: [BoneSpec] = [
        BoneSpec(id: "dot-l-elbow", a: 4, b: 4, radius: 0.045),
        BoneSpec(id: "dot-r-elbow", a: 5, b: 5, radius: 0.045),
        BoneSpec(id: "dot-l-knee", a: 10, b: 10, radius: 0.05),
        BoneSpec(id: "dot-r-knee", a: 11, b: 11, radius: 0.05),
    ]

    private func j(_ index: Int) -> simd_float3 {
        joints.indices.contains(index) ? joints[index] : .zero
    }

    var body: some Node {
        // Spine — the one bone that isn't joint-to-joint.
        salahBone(id: "spine", from: (j(2) + j(3)) * 0.5, to: (j(8) + j(9)) * 0.5,
                  radius: 0.055, color: color)
        ForEach3D(Self.bones) { bone in
            salahBone(id: bone.id, from: j(bone.a), to: j(bone.b),
                      radius: bone.radius, color: color)
        }
        ForEach3D(Self.jointDots) { dot in
            salahSphere(id: dot.id, position: j(dot.a), scale: dot.radius,
                        color: .white.opacity(0.85))
        }
        salahSphere(id: "head", position: j(0) + simd_float3(0, 0.02, 0), scale: 0.13, color: color)
    }
}

/// One identifiable point of the posture-coloured cloud.
private struct CloudItem: Identifiable {
    let id: Int
    let position: simd_float3
    let posture: Int
    let isPlayhead: Bool
}

private struct PointCloud: Node {
    var id: String { "cloud" }
    let points: [simd_float3]
    let postures: [Int]
    let playhead: simd_float3?

    var body: some Node {
        var nearest: Int? = nil
        if let playhead = playhead, !points.isEmpty {
            var best = 0
            var bestDistance = Float.greatestFiniteMagnitude
            for (index, point) in points.enumerated() {
                let distance = simd_length(point - playhead)
                if distance < bestDistance {
                    bestDistance = distance
                    best = index
                }
            }
            nearest = best
        }
        let items = points.indices.map { index in
            CloudItem(
                id: index,
                position: points[index],
                posture: postures.indices.contains(index) ? postures[index] : 8,
                isPlayhead: index == nearest,
            )
        }
        return ForEach3D(items) { item in
            CloudPointNode(item: item)
        }
    }
}

private struct CloudPointNode: Node {
    var id: String { "cloud-point-\(item.id)" }
    let item: CloudItem

    var body: some Node {
        if item.isPlayhead {
            salahSphere(id: "playhead-\(item.id)", position: item.position,
                        scale: 0.16, color: .white)
            salahSphere(id: "playhead-core-\(item.id)", position: item.position,
                        scale: 0.11, color: salahPostureColor(item.posture))
        } else {
            salahSphere(id: "point-\(item.id)", position: item.position,
                        scale: 0.04, color: salahPostureColor(item.posture))
        }
    }
}

/// The gravity vector against a world-frame axis cross.
private struct GravityScene: Node {
    var id: String { "gravity" }
    let vector: simd_float3

    var body: some Node {
        salahBone(id: "axis-x", from: simd_float3(-1.2, 0, 0), to: simd_float3(1.2, 0, 0),
                  radius: 0.012, color: .white.opacity(0.55))
        salahBone(id: "axis-z", from: simd_float3(0, 0, -1.2), to: simd_float3(0, 0, 1.2),
                  radius: 0.012, color: .white.opacity(0.55))
        salahBone(id: "gravity-arrow", from: simd_float3(0, 0, 0), to: vector,
                  radius: 0.03, color: gravityTeal)
        salahSphere(id: "gravity-tip", position: vector, scale: 0.12, color: gravityTip)
    }
}

// MARK: - The scene view

private struct SalahSceneView: View {
    @ObservedObject var state: SalahSceneState

    /// The orbit pivot and distance per scene: the humanoid pivots around its
    /// mid-height (~0.9 m) so the full figure stays framed through every pose,
    /// while the centered clouds orbit around the origin.
    private var cameraTransform: float4x4 {
        let isHumanoid = state.mode == 0
        let target = isHumanoid ? simd_float3(0, 0.9, 0) : simd_float3(0, 0, 0)
        let distance: Float = isHumanoid ? 2.8 : 3.4
        return float4x4.rotated(angle: state.camYaw, axis: .up) *
        float4x4.rotated(angle: state.camPitch, axis: .right) *
        float4x4.translated(.back * distance) *
        float4x4.translated(target)
    }

    var body: some View {
        Swift3DView(preferredFps: 30) {
            CameraNode(id: "cam")
                .perspective(fov: 0.9, zNear: 0.05, zFar: 50)
                .transform(cameraTransform)

            switch state.mode {
            case 0:
                HumanoidFigure(
                    joints: state.joints,
                    color: salahPostureColor(state.postureIndex))
            case 1, 3:
                PointCloud(
                    points: state.points,
                    postures: state.pointPostures,
                    playhead: state.playhead)
            default:
                GravityScene(vector: state.gravity)
            }
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { gesture in
                    let translation = gesture.translation
                    state.camYaw -= Float(translation.width) / 180
                    state.camPitch = max(
                        -1.35,
                        min(1.35, state.camPitch + Float(translation.height) / 180),
                    )
                }
        )
    }
}

// MARK: - The Kotlin-facing host

/// Implements the shared `Salah3DSceneService` (data) and the iOS-side
/// `Salah3DSceneViewProviding` (view) protocols. Kotlin pushes frames; the
/// SwiftUI scene rebuilds from the published state, and Swift3D's transitions
/// ease the geometry between versions.
final class SalahScene3DHost: NSObject, Salah3DSceneService, Salah3DSceneViewProviding {
    private let state = SalahSceneState()
    private var hostingController: UIHostingController<SalahSceneView>?

    func createSceneView() -> UIView {
        if let existing = hostingController?.view {
            return existing
        }
        let controller = UIHostingController(rootView: SalahSceneView(state: state))
        controller.view.backgroundColor = .clear
        hostingController = controller
        return controller.view
    }

    private func floats(_ array: [KotlinFloat]) -> [Float] {
        array.map { ($0 as NSNumber).floatValue }
    }

    private static func positions(from floats: [Float]) -> [simd_float3] {
        var positions: [simd_float3] = []
        positions.reserveCapacity(floats.count / 3)
        var index = 0
        while index + 2 < floats.count {
            positions.append(simd_float3(floats[index], floats[index + 1], floats[index + 2]))
            index += 3
        }
        return positions
    }

    func setSceneMode(mode: Int32) {
        let mode = Int(mode)
        DispatchQueue.main.async { [state] in
            state.mode = mode
        }
    }

    func updatePose(joints: [KotlinFloat], postureIndex: Int32) {
        let positions = Self.positions(from: floats(joints))
        let posture = Int(postureIndex)
        DispatchQueue.main.async { [state] in
            state.poseTarget = positions
            state.postureIndex = posture
        }
    }

    func updateScatter(points: [KotlinFloat], postureIndices: [KotlinInt]) {
        let positions = Self.positions(from: floats(points))
        let ordinals: [Int] = postureIndices.map { ($0 as NSNumber).intValue }
        DispatchQueue.main.async { [state] in
            state.points = positions
            state.pointPostures = ordinals
        }
    }

    func updatePlayhead(position: [KotlinFloat]) {
        let floats = floats(position)
        let playhead = floats.count >= 3 ? simd_float3(floats[0], floats[1], floats[2]) : nil
        DispatchQueue.main.async { [state] in
            state.playhead = playhead
        }
    }

    func updateGravity(vector: [KotlinFloat]) {
        let floats = floats(vector)
        let gravity = floats.count >= 3 ? simd_float3(floats[0], floats[1], floats[2]) : nil
        DispatchQueue.main.async { [state] in
            if let gravity = gravity {
                state.gravity = gravity
            }
        }
    }
}
