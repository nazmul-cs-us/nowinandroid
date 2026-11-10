package com.starception.submission.family

/** Matches the server's UTF-8 byte limit, including Arabic and emoji. */
class FamilyChatPolicy {
    fun valid(text: String): Boolean = text.isNotBlank() && text.encodeToByteArray().size <= 4096 &&
        text.none { (it.code < 32 && it !in "\t\n\r") || it.code == 127 }
    fun error(): String = "Write a message of up to 4 KB. Control characters are not supported."
}
