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
import llama

/// In-process port of Android's libdeenly_completion runner.
///
/// Android spawns llama.cpp as a CLI process (`--single-turn --predict 256
/// --json-schema … --temp 0`); iOS cannot exec, so the same generation runs
/// in-process against the llama C API with identical semantics: ChatML
/// prompt, a grammar converted from the same JSON schema, greedy sampling,
/// and a 256-token budget. The model loads per generation and is freed
/// afterwards, mirroring Android's process-per-request lifecycle.
enum DeenlyRunner {

    private static let queue = DispatchQueue(label: "com.starception.submission.deenly.runner")

    /// True while a generation is running; a second request is rejected,
    /// like Android's inference mutex.
    private static var isGenerating = false
    private static let generatingLock = NSLock()

    static func modelFileIsPresent(atPath path: String, expectedSize: Int) -> Bool {
        let attrs = try? FileManager.default.attributesOfItem(atPath: path)
        guard let size = attrs?[.size] as? Int else { return false }
        return size == expectedSize
    }

    /// Runs one single-turn, grammar-guided completion. `onComplete` is
    /// invoked on the main queue with the generated text, or nil when the
    /// model is missing, malformed, the generation times out, or another
    /// generation is already running.
    static func generate(
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maxTokens: Int = 256,
        timeoutSeconds: Int = 240,
        onComplete: @escaping (String?) -> Void,
    ) {
        generatingLock.lock()
        if isGenerating {
            generatingLock.unlock()
            DispatchQueue.main.async { onComplete(nil) }
            return
        }
        isGenerating = true
        generatingLock.unlock()

        queue.async {
            let result = runSingleTurn(
                modelPath: modelPath,
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                grammar: grammar,
                maxTokens: maxTokens,
                timeoutSeconds: timeoutSeconds,
            )
            generatingLock.lock()
            isGenerating = false
            generatingLock.unlock()
            DispatchQueue.main.async { onComplete(result) }
        }
    }

    private static func runSingleTurn(
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
        grammar: String,
        maxTokens: Int,
        timeoutSeconds: Int,
    ) -> String? {
        let startedAt = Date()
        llama_backend_init()

        let loadedModel: OpaquePointer?
        #if targetEnvironment(simulator)
        // Simulator Metal exposes a host GPU, but its residency-set support can
        // abort inside llama.cpp. An empty device list also prevents host-buffer
        // mapping through Metal; zero GPU layers alone does not guarantee that.
        var modelParams = llama_model_default_params()
        modelParams.n_gpu_layers = 0
        var devices: [ggml_backend_dev_t?] = [nil]
        loadedModel = devices.withUnsafeMutableBufferPointer { buffer in
            modelParams.devices = buffer.baseAddress
            return llama_model_load_from_file(modelPath, modelParams)
        }
        #else
        loadedModel = llama_model_load_from_file(modelPath, llama_model_default_params())
        #endif
        guard let model = loadedModel else {
            NSLog("DeenlyRunner: model load failed")
            return nil
        }
        defer { llama_model_free(model) }
        guard let vocab = llama_model_get_vocab(model) else {
            NSLog("DeenlyRunner: vocab unavailable")
            return nil
        }
        NSLog("DeenlyRunner: model loaded in %.1fs", Date().timeIntervalSince(startedAt))

        // Prompt + 256 generated tokens fit comfortably; Qwen's ChatML adds
        // only a handful of special tokens around the content.
        var contextParams = llama_context_default_params()
        contextParams.n_ctx = 2_048
        contextParams.n_batch = 1_024
        #if targetEnvironment(simulator)
        contextParams.offload_kqv = false
        contextParams.op_offload = false
        #endif
        guard let context = llama_init_from_model(model, contextParams) else {
            return nil
        }
        defer { llama_free(context) }

        let prompt = chatMLPrompt(system: systemPrompt, user: userPrompt)
        // Match llama-completion on Android and llama.cpp's reference runner.
        // Qwen's prompt still needs the vocabulary's leading special token in
        // addition to the explicit ChatML turn markers.
        guard let promptTokens = tokenize(vocab, prompt, addSpecial: true),
              !promptTokens.isEmpty else {
            NSLog("DeenlyRunner: prompt tokenization failed")
            return nil
        }
        guard decode(context: context, tokens: promptTokens, firstPosition: 0) else {
            NSLog("DeenlyRunner: prompt decode failed (%d tokens)", promptTokens.count)
            return nil
        }
        NSLog("DeenlyRunner: prompt decoded (%d tokens) in %.1fs", promptTokens.count, Date().timeIntervalSince(startedAt))

        // The generation budget starts after the prompt decode: the
        // simulator has no Metal GPU, and the CPU-only prompt pass alone can
        // exceed a minute, which must not eat the sampling budget.
        let deadline = Date().addingTimeInterval(TimeInterval(timeoutSeconds))

        let samplerChain = llama_sampler_chain_init(llama_sampler_chain_default_params())
        defer { llama_sampler_free(samplerChain) }
        if !grammar.isEmpty {
            guard let grammarSampler = llama_sampler_init_grammar(vocab, grammar, "root") else {
                NSLog("DeenlyRunner: grammar init failed")
                return nil
            }
            llama_sampler_chain_add(samplerChain, grammarSampler)
        }
        llama_sampler_chain_add(samplerChain, llama_sampler_init_greedy())

        var output = ""
        var position = Int64(promptTokens.count)
        var token = llama_sampler_sample(samplerChain, context, -1)
        var generated = 0
        while generated < maxTokens && !llama_vocab_is_eog(vocab, token) {
            if Date() >= deadline {
                NSLog("DeenlyRunner: timed out after %d tokens", generated)
                return nil
            }
            guard let piece = detokenize(vocab, token) else { return nil }
            output += piece
            generated += 1

            // Feed the sampled token id straight back; positions continue
            // from where the prompt batch ended.
            var tokenBuffer = [token]
            var positionBuffer = [llama_pos(position)]
            let decoded = tokenBuffer.withUnsafeMutableBufferPointer { tokenSlice -> Bool in
                positionBuffer.withUnsafeMutableBufferPointer { positionSlice -> Bool in
                    var batch = llama_batch_get_one(tokenSlice.baseAddress, 1)
                    batch.pos = positionSlice.baseAddress
                    return llama_decode(context, batch) == 0
                }
            }
            if !decoded { return nil }
            position += 1
            token = llama_sampler_sample(samplerChain, context, -1)
        }
        NSLog(
            "DeenlyRunner: finished %d tokens in %.1fs: %@",
            generated,
            Date().timeIntervalSince(startedAt),
            String(output.prefix(240))
        )
        return output
    }

    /// Qwen2.5 ChatML — the template libdeenly_completion applies through
    /// llama.cpp's chat machinery for these GGUFs.
    private static func chatMLPrompt(system: String, user: String) -> String {
        "<|im_start|>system\n\(system)<|im_end|>\n<|im_start|>user\n\(user)<|im_end|>\n<|im_start|>assistant\n"
    }

    private static func tokenize(
        _ vocab: OpaquePointer,
        _ text: String,
        addSpecial: Bool,
    ) -> [llama_token]? {
        var tokens = [llama_token](repeating: 0, count: text.utf8.count + 16)
        let count = text.withCString { cString -> Int32 in
            // parse_special = true so ChatML markers become single tokens.
            tokens.withUnsafeMutableBufferPointer { buffer in
                llama_tokenize(
                    vocab,
                    cString,
                    Int32(strlen(cString)),
                    buffer.baseAddress,
                    Int32(buffer.count),
                    addSpecial,
                    true,
                )
            }
        }
        if count <= 0 { return nil }
        return Array(tokens.prefix(Int(count)))
    }

    private static func decode(
        context: OpaquePointer,
        tokens: [llama_token],
        firstPosition: Int64,
    ) -> Bool {
        var mutableTokens = tokens
        var positions = (0..<tokens.count).map { llama_pos(firstPosition + Int64($0)) }
        return mutableTokens.withUnsafeMutableBufferPointer { tokenSlice -> Bool in
            guard let tokenBase = tokenSlice.baseAddress else { return false }
            return positions.withUnsafeMutableBufferPointer { positionSlice -> Bool in
                var batch = llama_batch_get_one(tokenBase, Int32(tokens.count))
                batch.pos = positionSlice.baseAddress
                return llama_decode(context, batch) == 0
            }
        }
    }

    private static func detokenize(_ vocab: OpaquePointer, _ token: llama_token) -> String? {
        var buffer = [CChar](repeating: 0, count: 256)
        let length = buffer.withUnsafeMutableBufferPointer { pointer -> Int32 in
            llama_token_to_piece(
                vocab,
                token,
                pointer.baseAddress,
                Int32(pointer.count),
                0,
                true,
            )
        }
        guard length > 0 else { return "" }
        guard Int(length) < buffer.count else { return nil }
        buffer[Int(length)] = 0
        return String(cString: buffer)
    }
}
