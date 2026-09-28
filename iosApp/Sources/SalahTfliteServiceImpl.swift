//
//  Copyright 2026 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import Foundation
import Shared
import ORTObjectiveC

/// Runs the Android-trained salah posture detector on-device through ONNX
/// Runtime — the iOS half of the shared SalahQualityAnalyzer. The Android
/// TFLite model was converted to ONNX with identical semantics: input "x"
/// [1, 20, 30] float, output "Identity" [1, 7] softmax probabilities.
final class SalahTfliteServiceImpl: NSObject, SalahTfliteService {

    private let queue = DispatchQueue(label: "com.starception.submission.salah-onnx")
    private var environment: ORTEnv?
    private var session: ORTSession?
    private var inputName: String?
    private var outputName: String?
    private var sequenceLength = 0
    private var featuresPerWindow = 0

    override init() {
        super.init()
        queue.sync { initializeOnQueue() }
    }

    private func initializeOnQueue() {
        guard let modelPath = Bundle(for: SalahTfliteServiceImpl.self)
            .path(forResource: "salah_detector", ofType: "onnx") else {
            NSLog("[SalahONNX] salah_detector.onnx missing from the bundle")
            return
        }
        guard let paramsPath = Bundle(for: SalahTfliteServiceImpl.self)
            .path(forResource: "salah_norm_params", ofType: "json"),
            let paramsText = try? String(contentsOfFile: paramsPath, encoding: .utf8),
            let params = try? JSONSerialization.jsonObject(with: Data(paramsText.utf8)) as? [String: Any],
            let seq = params["sequence_length"] as? Int,
            let feats = params["features_per_window"] as? Int else {
            NSLog("[SalahONNX] salah_norm_params.json missing or malformed")
            return
        }
        sequenceLength = seq
        featuresPerWindow = feats
        do {
            let env = try ORTEnv(loggingLevel: ORTLoggingLevel.warning)
            let options = try ORTSessionOptions()
            let loaded = try ORTSession(env: env, modelPath: modelPath, sessionOptions: options)
            environment = env
            session = loaded
            inputName = try loaded.inputNames().first
            outputName = try loaded.outputNames().first
            NSLog("[SalahONNX] session ready (seq=%d features=%d)", seq, feats)
        } catch {
            NSLog("[SalahONNX] init failed: %@", error.localizedDescription)
        }
    }

    func classify(sequence: KotlinArray<KotlinFloatArray>) -> KotlinFloatArray? {
        guard let session, let inputName, let outputName,
              Int(truncatingIfNeeded: sequence.size) == sequenceLength,
              sequence.size > 0 else { return nil }
        var flattened = [Float]()
        flattened.reserveCapacity(sequenceLength * featuresPerWindow)
        for i in 0..<Int(truncatingIfNeeded: sequence.size) {
            guard let window = sequence.get(index: Int32(i)) else { return nil }
            for j in 0..<Int(truncatingIfNeeded: window.size) {
                flattened.append(window.get(index: Int32(j)))
            }
        }
        var output: KotlinFloatArray? = nil
        dispatchPrecondition(condition: .notOnQueue(queue))
        queue.sync {
            do {
                let inputData = flattened.withUnsafeBufferPointer { buffer in
                    NSMutableData(bytes: buffer.baseAddress, length: flattened.count * MemoryLayout<Float>.size)
                }
                let shape: [NSNumber] = [NSNumber(value: 1), NSNumber(value: sequenceLength), NSNumber(value: featuresPerWindow)]
                let tensor = try ORTValue(
                    tensorData: inputData,
                    elementType: ORTTensorElementDataType.float,
                    shape: shape
                )
                let outputs = try session.run(
                    withInputs: [inputName: tensor],
                    outputNames: [outputName],
                    runOptions: nil
                )
                guard let result = outputs[outputName] else { return }
                let data = try result.tensorData()
                let shapeInfo = try result.tensorTypeAndShapeInfo()
                let count = shapeInfo.shape.reduce(1) { $0 * $1.intValue }
                let pointer = data.bytes.assumingMemoryBound(to: Float.self)
                let floats = Array(UnsafeBufferPointer(start: pointer, count: count))
                output = KotlinFloatArray(size: 7) { index in
                    KotlinFloat(value: floats[Int(index)])
                }
            } catch {
                NSLog("[SalahONNX] inference failed: %@", error.localizedDescription)
            }
        }
        return output
    }
}
