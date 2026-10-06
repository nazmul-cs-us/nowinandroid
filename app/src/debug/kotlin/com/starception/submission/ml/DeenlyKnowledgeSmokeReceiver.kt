/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.ml

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only entry point for proving inference occurs inside the installed app process. */
class DeenlyKnowledgeSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val sourceText = "Sovereign of the Day of Recompense"
            val result = DeenlyKnowledgeModel(context).generate(
                task = DeenlyKnowledgeTask.QUESTION,
                source = DeenlySourceEnvelope(
                    collection = "quran",
                    reference = "Quran 1:4",
                    topic = "The Opener",
                    sourceText = sourceText,
                    sourceTextSha256 = MessageDigest.getInstance("SHA-256")
                        .digest(sourceText.toByteArray())
                        .joinToString(separator = "") { "%02x".format(it) },
                ),
            )
            val accepted = result is DeenlyKnowledgeDecision.GroundedQuestion &&
                result.answer in sourceText &&
                result.evidence in sourceText &&
                result.answer in result.evidence &&
                result.options.count { it == result.answer } == 1
            Log.i(TAG, "accepted=$accepted result=$result")
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.starception.submission.debug.DEENLY_KNOWLEDGE_SMOKE"
        const val TAG = "DeenlyKnowledgeSmoke"
    }
}
