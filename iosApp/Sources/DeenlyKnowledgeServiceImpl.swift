/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

import Foundation
import Shared

/// iOS half of the shared DeenlyKnowledgeService: resolves the CDN-downloaded
/// GGUFs and runs them through the in-process llama.cpp runner
/// (DeenlyRunner), the port of Android's libdeenly_completion process.
final class DeenlyKnowledgeServiceImpl: NSObject, DeenlyKnowledgeService {

    static let shared = DeenlyKnowledgeServiceImpl()

    /// Same sizes as Android's DeenlyKnowledgeModel; a partial download does
    /// not count as present.
    private static let questionModelFile = "deenly-question-v5-q4_k_m.gguf"
    private static let questionModelSize = 986_048_224
    private static let answerModelFile = "deenly-knowledge-v3-q4_k_m.gguf"
    private static let answerModelSize = 986_048_224

    func modelPathForTask(task: String) -> String? {
        let file: String
        let expectedSize: Int
        switch task {
        case "answer":
            file = Self.answerModelFile
            expectedSize = Self.answerModelSize
        default:
            file = Self.questionModelFile
            expectedSize = Self.questionModelSize
        }
        // The shared CDN downloader commits assets under
        // Application Support/StarceptionAssets/<cdnKey>.
        let url = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("StarceptionAssets/models/now_nudge/\(file)")
        guard DeenlyRunner.modelFileIsPresent(atPath: url.path, expectedSize: expectedSize) else {
            return nil
        }
        return url.path
    }

    func run(
        modelPath: String,
        task: String,
        systemPrompt: String,
        userPrompt: String,
        onComplete: @escaping (String?) -> Void
    ) {
        let sourceContext = userPrompt
            .split(separator: "\n")
            .filter { $0.hasPrefix("Collection:") || $0.hasPrefix("Reference:") }
            .joined(separator: " | ")
        NSLog("DeenlyKnowledgeService: task=%@ %@", task, sourceContext)
        DeenlyRunner.generate(
            modelPath: modelPath,
            systemPrompt: systemPrompt,
            userPrompt: userPrompt,
            grammar: DeenlyGrammar.forTask(task),
            onComplete: onComplete
        )
    }
}
