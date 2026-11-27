package com.starception.submission.voice

import java.security.MessageDigest

/** A 32-bit String.hashCode is not an identity: distinct narrations can collide. */
internal fun ttsCacheIdentity(text: String, modelIdentity: String, speakerId: Int, speed: Float): String {
    val fields = listOf("tts-source-v2", modelIdentity, speakerId.toString(), speed.toBits().toString(), text)
    val encoded = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
}

internal fun isTtsCacheIdentity(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
